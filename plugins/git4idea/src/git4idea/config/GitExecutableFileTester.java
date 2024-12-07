// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.AppJavaExecutorUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class GitExecutableFileTester {
  private static final Logger LOG = Logger.getInstance(GitExecutableFileTester.class);
  private static final int FILE_TEST_TIMEOUT_MS = 30000;

  private final ReentrantLock LOCK = new ReentrantLock();
  private final @NotNull ConcurrentMap<GitExecutable, TestResult> myTestMap = new ConcurrentHashMap<>();

  final @NotNull TestResult getResultFor(@NotNull GitExecutable executable) {
    return ProgressIndicatorUtils.computeWithLockAndCheckingCanceled(LOCK, 50, TimeUnit.MILLISECONDS, () -> {
      TestResult result = myTestMap.get(executable);
      long currentLastModificationDate = 0L;

      try {
        currentLastModificationDate = getModificationTime(executable);
        if (result == null || result.getFileLastModifiedTimestamp() != currentLastModificationDate) {
          result = new TestResult(testOrAbort(executable), currentLastModificationDate);
          myTestMap.put(executable, result);
        }
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Exception e) {
        LOG.warn(e);

        result = new TestResult(e, currentLastModificationDate);
        myTestMap.put(executable, result);
      }

      return result;
    });
  }

  private static long getModificationTime(@NotNull GitExecutable executable) throws IOException {
    if (executable instanceof GitExecutable.Unknown) {
      return 0;
    }

    if (executable instanceof GitExecutable.Local) {
      String filePath = executable.getExePath();
      if (!filePath.contains(File.separator)) {
        File exeFile = PathEnvironmentVariableUtil.findInPath(filePath);
        if (exeFile != null) filePath = exeFile.getPath();
      }

      Path executablePath = Paths.get(filePath);
      long modificationTime = getModificationTime(executablePath);

      for (Path dependencyPath : GitExecutableDetector.getDependencyPaths(executablePath)) {
        try {
          long depTime = getModificationTime(dependencyPath);
          modificationTime = Math.max(modificationTime, depTime);
        }
        catch (IOException ignore) {
        }
      }

      return modificationTime;
    }

    if (executable instanceof GitExecutable.Wsl) {
      return 0;
    }

    LOG.error("Can't get modification time for " + executable);
    return 0;
  }

  private static long getModificationTime(@NotNull Path filePath) throws IOException {
    return Files.getLastModifiedTime(filePath).toMillis();
  }

  private static @NotNull GitVersion testOrAbort(@NotNull GitExecutable executable) throws Exception {
    int maxAttempts = 1;

    // IDEA-248193 Apple Git might hang with timeout after hibernation. Do several attempts.
    if (SystemInfo.isMac && "/usr/bin/git".equals(executable.getExePath())) {
      maxAttempts = 3;
    }

    int attempt = 0;
    while (attempt < maxAttempts) {
      GitVersion result = runTestWithTimeout(executable);
      if (result != null) return result;
      attempt++;
    }

    throw new GitVersionIdentificationException(
      GitBundle.message("git.executable.validation.error.no.response.in.n.attempts.message", maxAttempts), null);
  }

  private static @Nullable GitVersion runTestWithTimeout(@NotNull GitExecutable executable) throws Exception {
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    Ref<Exception> exceptionRef = new Ref<>();
    Ref<GitVersion> resultRef = new Ref<>();

    Semaphore semaphore = new Semaphore(0);

    AppJavaExecutorUtil.executeOnPooledIoThread(() -> {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          resultRef.set(testExecutable(executable));
        }
        catch (Exception e) {
          exceptionRef.set(e);
        }
        finally {
          semaphore.release();
        }
      }, indicator);
    });

    try {
      long start = System.currentTimeMillis();
      while (true) {
        ProgressManager.checkCanceled();
        if (semaphore.tryAcquire(50, TimeUnit.MILLISECONDS)) break;
        if (System.currentTimeMillis() - start > FILE_TEST_TIMEOUT_MS) break;
      }
      if (!resultRef.isNull()) return resultRef.get();
      if (!exceptionRef.isNull()) throw exceptionRef.get();
      return null; // timeout
    }
    finally {
      indicator.cancel();
    }
  }

  public @Nullable TestResult getCachedResultFor(@NotNull GitExecutable executable) {
    return myTestMap.get(executable);
  }

  public void dropCache(@NotNull GitExecutable executable) {
    myTestMap.remove(executable);
  }

  public void dropCache() {
    myTestMap.clear();
  }

  private static @NotNull GitVersion testExecutable(@NotNull GitExecutable executable) throws Exception {
    GitVersion.Type type = null;
    File workingDirectory = new File(".");
    if (executable instanceof GitExecutable.Unknown) {
      type = GitVersion.Type.UNDEFINED;
    }
    else if (executable instanceof GitExecutable.Wsl) {
      WSLDistribution distribution = ((GitExecutable.Wsl)executable).getDistribution();
      type = distribution.getVersion() == 1 ? GitVersion.Type.WSL1 : GitVersion.Type.WSL2;
      workingDirectory = new File(distribution.getWindowsPath("/"));
    }

    LOG.debug("Acquiring git version for " + executable);
    GitLineHandler handler = new GitLineHandler(null,
                                                workingDirectory,
                                                executable,
                                                GitCommand.VERSION,
                                                Collections.emptyList());
    handler.setPreValidateExecutable(false);
    handler.setSilent(false);
    handler.setTerminationTimeout(1000);
    handler.setStdoutSuppressed(false);
    GitCommandResult result = Git.getInstance().runCommand(handler);
    String rawResult = result.getOutputOrThrow();
    GitVersion version = GitVersion.parse(rawResult, type);
    LOG.info("Git version for " + executable + ": " + version);
    return version;
  }

  public static class TestResult {
    private final @Nullable GitVersion myResult;
    private final @Nullable Exception myException;
    private final long myFileLastModifiedTimestamp;

    TestResult(@NotNull GitVersion result, long timestamp) {
      myResult = result;
      myFileLastModifiedTimestamp = timestamp;
      myException = null;
    }

    TestResult(@NotNull Exception exception, long timestamp) {
      myFileLastModifiedTimestamp = timestamp;
      myResult = null;
      myException = exception;
    }

    public @Nullable GitVersion getResult() {
      return myResult;
    }

    public @Nullable Exception getException() {
      return myException;
    }

    private long getFileLastModifiedTimestamp() {
      return myFileLastModifiedTimestamp;
    }
  }
}