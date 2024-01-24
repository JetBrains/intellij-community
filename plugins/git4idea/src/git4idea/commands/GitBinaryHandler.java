// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.config.GitExecutable;
import git4idea.i18n.GitBundle;
import git4idea.util.GitVcsConsoleWriter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The handler that allows consuming binary data as byte array
 */
public class GitBinaryHandler extends GitHandler {
  private static final int BUFFER_SIZE = 8 * 1024;

  private final @NotNull ByteArrayOutputStream myStdout = new ByteArrayOutputStream();
  private final @NotNull ByteArrayOutputStream myStderr = new ByteArrayOutputStream();
  private final @NotNull Semaphore mySteamSemaphore = new Semaphore(0); // The semaphore that waits for stream processing
  private final @NotNull AtomicReference<VcsException> myException = new AtomicReference<>();

  public GitBinaryHandler(@Nullable Project project, @NotNull VirtualFile vcsRoot, @NotNull GitCommand command) {
    super(project, vcsRoot, command, Collections.emptyList());
  }

  public GitBinaryHandler(@NotNull File directory, @NotNull GitExecutable pathToExecutable, @NotNull GitCommand command) {
    super(null, directory, pathToExecutable, command, Collections.emptyList());
  }

  @Override
  protected Process startProcess() throws ExecutionException {
    return myCommandLine.createProcess();
  }

  @Override
  protected void startHandlingStreams() {
    handleStream(myProcess.getErrorStream(), myStderr, "Error stream copy of " + myCommandLine.getCommandLineString());
    handleStream(myProcess.getInputStream(), myStdout, "Output stream copy of " + myCommandLine.getCommandLineString());
  }

  /**
   * Handle the single stream
   *
   * @param in  the standard input
   * @param out the standard output
   */
  private void handleStream(final InputStream in, final ByteArrayOutputStream out, @NotNull @NonNls String cmd) {
    Thread t = new Thread(() -> {
      try {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
          int rc = in.read(buffer);
          if (rc == -1) {
            break;
          }
          out.write(buffer, 0, rc);
        }
      }
      catch (IOException e) {
        if (!myException.compareAndSet(null, new VcsException(GitBundle.message("git.error.cant.process.output", e.getLocalizedMessage()), e))) {
          LOG.error("Problem reading stream", e);
        }
      }
      finally {
        mySteamSemaphore.release(1);
      }
    }, cmd);
    t.setDaemon(true);
    t.start();
  }

  @Override
  protected void waitForProcess() {
    int exitCode;
    try {
      while (!mySteamSemaphore.tryAcquire(2, 50, TimeUnit.MILLISECONDS)) {
        ProgressManager.checkCanceled();
      }
      while (!myProcess.waitFor(50, TimeUnit.MILLISECONDS)) {
        ProgressManager.checkCanceled();
      }
      exitCode = myProcess.exitValue();
    }
    catch (InterruptedException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Ignoring process exception: ", e);
      }
      exitCode = 255;
    }
    OUTPUT_LOG.debug(String.format("%s %% %s terminated (%s)", getCommand(), this.hashCode(), exitCode));
    setExitCode(exitCode);
    listeners().processTerminated(exitCode);
  }

  /**
   * Run in the current thread and return the data as array
   *
   * @return the binary data
   * @throws VcsException in case of the problem with running git
   */
  public byte @NotNull [] run() throws VcsException {
    Project project = project();
    GitVcsConsoleWriter vcsConsoleWriter = project != null
                                           ? GitVcsConsoleWriter.getInstance(project)
                                           : null;

    addListener(new GitHandlerListener() {
      @Override
      public void processTerminated(int exitCode) {
        if (exitCode != 0) {
          Charset cs = getCharset();
          String message = new String(myStderr.toByteArray(), cs);
          if (message.isEmpty()) {
            if (myException.get() != null) {
              message = IdeCoreBundle.message("finished.with.exit.code.text.message", exitCode);
            }
            else {
              message = null;
            }
          }
          else {
            if (vcsConsoleWriter != null && !isStderrSuppressed()) {
              vcsConsoleWriter.showErrorMessage(message);
            }
          }
          if (message != null) {
            VcsException e = myException.getAndSet(new VcsException(message));
            if (e != null) {
              LOG.warn("Dropping previous exception: ", e);
            }
          }
        }
      }

      @Override
      public void startFailed(@NotNull Throwable exception) {
        VcsException err = new VcsException(GitBundle.message("git.executable.unknown.error.message", exception.getMessage()), exception);
        VcsException oldErr = myException.getAndSet(err);
        if (oldErr != null) {
          LOG.warn("Dropping previous exception: ", oldErr);
        }
      }
    });
    if (vcsConsoleWriter != null && !mySilent) {
      vcsConsoleWriter.showCommandLine("[" + GitImpl.stringifyWorkingDir(project.getBasePath(), getWorkingDirectory()) + "] "
                                       + printableCommandLine());
    }
    try {
      runInCurrentThread();
    }
    catch (IOException e) {
      throw new VcsException(e.getMessage(), e);
    }
    if (myException.get() != null) {
      throw myException.get();
    }
    return myStdout.toByteArray();
  }
}
