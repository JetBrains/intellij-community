/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.openapi.vcs.LineProcessEventListener;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.AuthenticationCallback;
import org.jetbrains.idea.svn.Util;
import org.jetbrains.idea.svn.config.SvnBindException;

import java.io.File;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 4:05 PM
 *
 * honestly stolen from GitLineHandler
 */
public class SvnLineCommand extends SvnCommand {
  public static final String AUTHENTICATION_REALM = "Authentication realm:";
  public static final String CERTIFICATE_ERROR = "Error validating server certificate for";
  /**
   * the partial line from stdout stream
   */
  private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  private final StringBuilder myStderrLine = new StringBuilder();
  private final EventDispatcher<LineProcessEventListener> myLineListeners;
  private final AtomicReference<Integer> myExitCode;
  private final StringBuffer myErr;

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath) {
    this(workingDirectory, commandName, exePath, null);
  }

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath, File configDir) {
    super(workingDirectory, commandName, exePath, configDir);
    myLineListeners = EventDispatcher.create(LineProcessEventListener.class);
    myExitCode = new AtomicReference<Integer>();
    myErr = new StringBuffer();
  }

  @Override
  protected void processTerminated(int exitCode) {
    // force newline
    if (myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }

  public static void runAndWaitProcessErrorsIntoExceptions(final String exePath, final File firstFile, SvnCommandName commandName,
                                                           final LineCommandListener listener, @Nullable AuthenticationCallback authenticationCallback, final String... parameters) throws SvnBindException {
    File base = firstFile.isDirectory() ? firstFile : firstFile.getParentFile();
    base = Util.correctUpToExistingParent(base);

    listener.baseDirectory(base);

    boolean certificateAttempt = false;
    boolean authenticationAsked = false;
    File configDir = null;
    while (true) {
      final SvnLineCommand command = runCommand(exePath, commandName, listener, base, configDir, parameters);
      if (command.myErr.length() > 0) {
        final String errText = command.myErr.toString().trim();
        if ((errText.startsWith(AUTHENTICATION_REALM) || errText.startsWith(CERTIFICATE_ERROR)) && authenticationCallback != null) {
          cleanup(exePath, commandName, base);
          if (errText.startsWith(CERTIFICATE_ERROR)) {
            final int idx = errText.indexOf('\n');
            if (idx == -1) {
              throw new SvnBindException("Can not detect authentication realm name: " + errText);
            }
            String realm = errText.substring(CERTIFICATE_ERROR.length(), idx);
            final int idx1 = realm.indexOf('\'');
            if (idx1 == -1) {
              throw new SvnBindException("Can not detect authentication realm name: " + errText);
            }
            final int idx2 = realm.indexOf('\'', idx1 + 1);
            if (idx2== -1) {
              throw new SvnBindException("Can not detect authentication realm name: " + errText);
            }
            realm = realm.substring(idx1 + 1, idx2);
            if (! certificateAttempt && authenticationCallback.acceptSSLServerCertificate(base, realm)) {
              certificateAttempt = true;
              if (authenticationCallback.getSpecialConfigDir() != null) {
                configDir = authenticationCallback.getSpecialConfigDir();
              }
              continue;
            }
            throw new SvnBindException("Server SSL certificate rejected");
          } else {
            final int idx = errText.indexOf('\n');
            if (idx == -1) {
              throw new SvnBindException("Can not detect authentication realm name: " + errText);
            }
            final String realm = errText.substring(AUTHENTICATION_REALM.length(), idx).trim();
            if (authenticationAsked) {
              authenticationCallback.clearPassiveCredentials(realm, base);
            }
            authenticationAsked = true;
            if (authenticationCallback.authenticateFor(realm, base, configDir != null)) {
              if (authenticationCallback.getSpecialConfigDir() != null) {
                configDir = authenticationCallback.getSpecialConfigDir();
              }
              continue;
            }
            throw new SvnBindException("Authentication canceled for realm: " + realm);
          }
        }
        throw new SvnBindException(errText);
      }
      final Integer exitCode = command.myExitCode.get();
      if (exitCode != 0) {
        throw new SvnBindException("Svn process exited with error code: " + exitCode);
      }
      return;
    }
    //ok
  }

  private static void cleanup(String exePath, SvnCommandName commandName, File base) throws SvnBindException {
    File wcRoot = Util.getWcRoot(base);
    if (wcRoot == null) throw new SvnBindException("Can not find working copy root for: " + base.getPath());

    //cleanup -> check command type
    if (commandName.isWriteable()) {
      final SvnSimpleCommand command = new SvnSimpleCommand(wcRoot, SvnCommandName.cleanup, exePath);
      try {
        command.run();
      }
      catch (VcsException e) {
        throw new SvnBindException(e);
      }
    }
  }

  /*svn: E170001: Commit failed (details follow):
  svn: E170001: Unable to connect to a repository at URL 'htt../svn/secondRepo/local2/trunk/mod2/src/com/test/gggGA'
  svn: E170001: OPTIONS of 'htt.../svn/secondRepo/local2/trunk/mod2/src/com/test/gggGA': authorization failed: Could not authenticate to server: rejected Basic challenge (ht)*/
  private final static String ourAuthFailed = "authorization failed";
  private final static String ourAuthFailed2 = "Could not authenticate to server";

  private static boolean isAuthenticationFailed(String s) {
    return s.trim().startsWith(AUTHENTICATION_REALM);
    //return s.contains(ourAuthFailed) && s.contains(ourAuthFailed2);
  }

  private static SvnLineCommand runCommand(String exePath,
                                           SvnCommandName commandName,
                                           final LineCommandListener listener,
                                           File base, File configDir,
                                           String... parameters) throws SvnBindException {
    final SvnLineCommand command = new SvnLineCommand(base, commandName, exePath, configDir);

    //command.addParameters("--non-interactive");
    command.addParameters(parameters);
    final AtomicReference<Throwable> exceptionRef = new AtomicReference<Throwable>();
    // several threads
    command.addListener(new LineProcessEventListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (SvnCommand.LOG.isDebugEnabled()) {
          SvnCommand.LOG.debug("==> " + line);
        }
        listener.onLineAvailable(line, outputType);
        if (listener.isCanceled()) {
          command.destroyProcess();
          return;
        }
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          if (command.myErr.length() > 0) {
            command.myErr.append('\n');
          }
          command.myErr.append(line);
          if (line.trim().startsWith(AUTHENTICATION_REALM) || line.trim().startsWith(CERTIFICATE_ERROR)) {
            command.destroyProcess();
            return;
          }
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        listener.processTerminated(exitCode);
        command.myExitCode.set(exitCode);
      }

      @Override
      public void startFailed(Throwable exception) {
        listener.startFailed(exception);
        exceptionRef.set(exception);
      }
    });
    command.start();
    command.waitFor();
    if (exceptionRef.get() != null) {
      throw new SvnBindException(exceptionRef.get());
    }
    return command;
  }

  @Override
  protected void onTextAvailable(String text, Key outputType) {
    Iterator<String> lines = LineHandlerHelper.splitText(text).iterator();
    if (ProcessOutputTypes.STDOUT == outputType) {
      notifyLines(outputType, lines, myStdoutLine);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      notifyLines(outputType, lines, myStderrLine);
    }
  }

  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (!lines.hasNext()) return;
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        notifyLine(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = null;
      if (lines.hasNext()) {
        line = lines.next();
      }

      if (lines.hasNext()) {
        notifyLine(line, outputType);
      }
      else {
        if (line != null && line.length() > 0) {
          lineBuilder.append(line);
        }
        break;
      }
    }
  }

  private void notifyLine(final String line, final Key outputType) {
    String trimmed = LineHandlerHelper.trimLineSeparator(line);
    myLineListeners.getMulticaster().onLineAvailable(trimmed, outputType);
  }

  public void addListener(LineProcessEventListener listener) {
    myLineListeners.addListener(listener);
    super.addListener(listener);
  }
}
