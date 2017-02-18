/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The handler that allows consuming binary data as byte array
 */
public class GitBinaryHandler extends GitHandler {
  private static final int BUFFER_SIZE = 8 * 1024;

  @NotNull private final ByteArrayOutputStream myStdout = new ByteArrayOutputStream();
  @NotNull private final ByteArrayOutputStream myStderr = new ByteArrayOutputStream();
  @NotNull private final Semaphore mySteamSemaphore = new Semaphore(0); // The semaphore that waits for stream processing
  @NotNull private final AtomicReference<VcsException> myException = new AtomicReference<>();

  public GitBinaryHandler(final Project project, final VirtualFile vcsRoot, final GitCommand command) {
    super(project, vcsRoot, command);
  }

  @Override
  protected Process startProcess() throws ExecutionException {
    return myCommandLine.createProcess();
  }

  @Override
  protected void startHandlingStreams() {
    handleStream(myProcess.getErrorStream(), myStderr, "Error stream copy of "+myCommandLine.getCommandLineString());
    handleStream(myProcess.getInputStream(), myStdout, "Output stream copy of "+myCommandLine.getCommandLineString());
  }

  /**
   * Handle the single stream
   * @param in  the standard input
   * @param out the standard output
   */
  private void handleStream(final InputStream in, final ByteArrayOutputStream out, @NotNull String cmd) {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
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
          //noinspection ThrowableInstanceNeverThrown
          if (!myException.compareAndSet(null, new VcsException("Stream IO problem", e))) {
            LOG.error("Problem reading stream", e);
          }
        }
        finally {
          mySteamSemaphore.release(1);
        }
      }
    }, cmd);
    t.setDaemon(true);
    t.start();
  }

  @Override
  public void destroyProcess() {
    myProcess.destroy();
  }

  @Override
  protected void waitForProcess() {
    try {
      mySteamSemaphore.acquire(2);
      myProcess.waitFor();
      int exitCode = myProcess.exitValue();
      setExitCode(exitCode);
    }
    catch (InterruptedException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Ignoring process exception: ", e);
      }
      setExitCode(255);
    }
    listeners().processTerminated(getExitCode());
  }

  /**
   * Run in the current thread and return the data as array
   *
   * @return the binary data
   * @throws VcsException in case of the problem with running git
   */
  public byte[] run() throws VcsException {
    addListener(new GitHandlerListener() {
      @Override
      public void processTerminated(int exitCode) {
        if (exitCode != 0 && !isIgnoredErrorCode(exitCode)) {
          Charset cs = getCharset();
          String message = new String(myStderr.toByteArray(), cs);
          if (message.length() == 0) {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (myException.get() != null) {
              message = IdeBundle.message("finished.with.exit.code.text.message", exitCode);
            }
            else {
              message = null;
            }
          }
          else {
            if (!isStderrSuppressed()) {
              GitVcs.getInstance(myProject).showErrorMessages(message);
            }
          }
          if (message != null) {
            //noinspection ThrowableInstanceNeverThrown
            VcsException e = myException.getAndSet(new VcsException(message));
            if (e != null) {
              LOG.warn("Dropping previous exception: ", e);
            }
          }
        }
      }

      @Override
      public void startFailed(Throwable exception) {
        //noinspection ThrowableInstanceNeverThrown
        VcsException e = myException.getAndSet(new VcsException("Start failed: " + exception.getMessage(), exception));
        if (e != null) {
          LOG.warn("Dropping previous exception: ", e);
        }
      }
    });
    GitHandlerUtil.runInCurrentThread(this, null);
    //noinspection ThrowableResultOfMethodCallIgnored
    if (myException.get() != null) {
      throw myException.get();
    }
    return myStdout.toByteArray();
  }
}
