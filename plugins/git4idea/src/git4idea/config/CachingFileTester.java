/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package git4idea.config;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

abstract class CachingFileTester {
  private static final Logger LOG = Logger.getInstance(CachingFileTester.class);
  private static final int FILE_TEST_TIMEOUT_MS = 30000;

  private final ReentrantLock LOCK = new ReentrantLock();
  @NotNull private final ConcurrentMap<GitExecutable, TestResult> myTestMap = new ConcurrentHashMap<>();

  @NotNull
  final TestResult getResultFor(@NotNull GitExecutable executable) {
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
      return Files.getLastModifiedTime(Paths.get(filePath)).toMillis();
    }

    if (executable instanceof GitExecutable.Wsl) {
      return 0;
    }

    LOG.error("Can't get modification time for " + executable);
    return 0;
  }

  @NotNull
  private GitVersion testOrAbort(@NotNull GitExecutable executable) throws Exception {
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

  @Nullable
  private GitVersion runTestWithTimeout(@NotNull GitExecutable executable) throws Exception {
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    Ref<Exception> exceptionRef = new Ref<>();
    Ref<GitVersion> resultRef = new Ref<>();

    Semaphore semaphore = new Semaphore(0);

    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          resultRef.set(testExecutable(executable));
        }
        catch (Exception e) {
          exceptionRef.set(e);
        }
        finally {
          semaphore.release();
        }
      }, indicator));

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

  @Nullable
  public TestResult getCachedResultFor(@NotNull GitExecutable executable) {
    return myTestMap.get(executable);
  }

  public void dropCache(@NotNull GitExecutable executable) {
    myTestMap.remove(executable);
  }

  @NotNull
  protected abstract GitVersion testExecutable(@NotNull GitExecutable executable) throws Exception;

  public static class TestResult {
    @Nullable private final GitVersion myResult;
    @Nullable private final Exception myException;
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

    @Nullable
    public GitVersion getResult() {
      return myResult;
    }

    @Nullable
    public Exception getException() {
      return myException;
    }

    private long getFileLastModifiedTimestamp() {
      return myFileLastModifiedTimestamp;
    }
  }
}