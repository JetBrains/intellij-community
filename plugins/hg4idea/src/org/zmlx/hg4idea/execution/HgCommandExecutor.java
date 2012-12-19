// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.execution;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.util.HgEncodingUtil;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Executes an hg external command synchronously or asynchronously with the consequent call of {@link HgCommandResultHandler}</p>
 *
 * <p>Silence policy:
 * <li>if the command is silent, the fact of its execution will be recorded in the log, but not in the VCS console.
 * <li>if the command is not silent, which is default, it is written in the log and console.
 * <li>the command output is not written to the log or shown to console by default, but it can be changed via {@link #myShowOutput}
 * <li>error output is logged to the console and log, if the command is not silent.
 * </p>
 */
public final class HgCommandExecutor {

  private static final Logger LOG = Logger.getInstance(HgCommandExecutor.class.getName());
  private static final List<String> DEFAULT_OPTIONS = Arrays.asList("--config", "ui.merge=internal:merge");

  private final Project myProject;
  private final HgVcs myVcs;

  private Charset myCharset = HgEncodingUtil.getDefaultCharset();
  private boolean myIsSilent = false;
  private boolean myShowOutput = false;
  private List<String> myOptions = DEFAULT_OPTIONS;

  public HgCommandExecutor(Project project) {
    myProject = project;
    myVcs = HgVcs.getInstance(myProject);
  }

  public void setCharset(Charset charset) {
    myCharset = charset;
  }

  public void setSilent(boolean isSilent) {
    myIsSilent = isSilent;
  }

  public void setOptions(List<String> options) {
    myOptions = options;
  }

  public void setShowOutput(boolean showOutput) {
    myShowOutput = showOutput;
  }

  public void execute(@Nullable final VirtualFile repo,
                      final String operation,
                      final List<String> arguments,
                      @Nullable final HgCommandResultHandler handler) {
    HgUtil.executeOnPooledThreadIfNeeded(new Runnable() {
      @Override
      public void run() {
        HgCommandResult result = executeInCurrentThread(repo, operation, arguments);
        if (handler != null) {
          handler.process(result);
        }
      }
    });
  }

  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo, @NotNull final String operation,
                                                @Nullable final List<String> arguments) {
    return executeInCurrentThread(repo, operation, arguments, null);
  }

  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo, @NotNull final String operation,
                                                @Nullable final List<String> arguments, @Nullable HgPromptHandler handler) {
    HgCommandResult result = executeInCurrentThread(repo, operation, arguments, handler, false);
    if (HgErrorUtil.isAuthorizationError(result)) {
      result = executeInCurrentThread(repo, operation, arguments, handler, true);
    }
    return result;
  }

  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo,
                                                @NotNull final String operation,
                                                @Nullable final List<String> arguments,
                                                @Nullable HgPromptHandler handler,
                                                boolean forceAuthorization) {
    //LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread()); disabled for release
    if (myProject == null || myProject.isDisposed() || myVcs == null) {
      return null;
    }

    final List<String> cmdLine = new LinkedList<String>();
    cmdLine.add(myVcs.getHgExecutable());
    if (repo != null) {
      cmdLine.add("--repository");
      cmdLine.add(repo.getPath());
    }

    WarningReceiver warningReceiver = new WarningReceiver();
    PassReceiver passReceiver = new PassReceiver(myProject, forceAuthorization);

    SocketServer promptServer = new SocketServer(new PromptReceiver(handler));
    SocketServer warningServer = new SocketServer(warningReceiver);
    SocketServer passServer = new SocketServer(passReceiver);

    try {
      int promptPort = promptServer.start();
      int warningPort = warningServer.start();
      int passPort = passServer.start();
      cmdLine.add("--config");
      cmdLine.add("extensions.hg4ideapromptextension=" + myVcs.getPromptHooksExtensionFile().getAbsolutePath());
      cmdLine.add("--config");
      cmdLine.add("hg4ideaprompt.port=" + promptPort);
      cmdLine.add("--config");
      cmdLine.add("hg4ideawarn.port=" + warningPort);
      cmdLine.add("--config");
      cmdLine.add("hg4ideapass.port=" + passPort);

      // Other parts of the plugin count on the availability of the MQ extension, so make sure it is enabled
      cmdLine.add("--config");
      cmdLine.add("extensions.mq=");
    } catch (IOException e) {
      showError(e);
      LOG.info("IOException during preparing command", e);
      promptServer.stop();
      warningServer.stop();
      passServer.stop();
      return null;
    }
    cmdLine.addAll(myOptions);
    cmdLine.add(operation);
    if (arguments != null && arguments.size() != 0) {
      cmdLine.addAll(arguments);
    }
    ShellCommand shellCommand = new ShellCommand(myVcs.getGlobalSettings().isRunViaBash());
    HgCommandResult result;
    try {
      String workingDir = repo != null ? repo.getPath() : null;
      result = shellCommand.execute(cmdLine, workingDir, myCharset);
      if (!HgErrorUtil.isAuthorizationError(result)) {
        passReceiver.saveCredentials();
      }
    } catch (ShellCommandException e) {
      if (myVcs.getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
        // if the problem was not with invalid executable - show error.
        showError(e);
        LOG.info(e.getMessage(), e);
      }
      return null;
    } catch (InterruptedException e) { // this may happen during project closing, no need to notify the user.
      LOG.info(e.getMessage(), e);
      return null;
    } finally {
      promptServer.stop();
      warningServer.stop();
      passServer.stop();
    }
    String warnings = warningReceiver.getWarnings();
    result.setWarnings(warnings);

    log(operation, arguments, result);
    return result;
  }

  // logging to the Version Control console (without extensions and configs)
  private void log(@NotNull String operation, @Nullable List<String> arguments, @NotNull HgCommandResult result) {
    final HgGlobalSettings settings = myVcs.getGlobalSettings();
    String exeName;
    final int lastSlashIndex = settings.getHgExecutable().lastIndexOf(File.separator);
    exeName = settings.getHgExecutable().substring(lastSlashIndex + 1);

    final String executable = settings.isRunViaBash() ? "bash -c " + exeName : exeName;
    final String cmdString = String.format("%s %s %s", executable, operation, arguments == null ? "" : StringUtil.join(arguments, " "));

    // log command
    if (!myIsSilent) {
      LOG.info(cmdString);
      myVcs.showMessageInConsole(cmdString, ConsoleViewContentType.NORMAL_OUTPUT.getAttributes());
    }
    else {
      LOG.debug(cmdString);
    }

    // log output if needed
    if (!result.getRawOutput().isEmpty()) {
      if (!myIsSilent && myShowOutput) {
        LOG.info(result.getRawOutput());
        myVcs.showMessageInConsole(result.getRawOutput(), ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
      }
      else {
        LOG.debug(result.getRawOutput());
      }
    }

    // log error
    if (!result.getRawError().isEmpty()) {
      if (!myIsSilent) {
        LOG.info(result.getRawError());
        myVcs.showMessageInConsole(result.getRawError(), ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
      }
      else {
        LOG.debug(result.getRawError());
      }
    }
  }

  private void showError(Exception e) {
    final HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs == null) { return; }

    StringBuilder message = new StringBuilder();
    message.append(HgVcsMessages.message("hg4idea.command.executable.error",
      vcs.getHgExecutable()))
      .append("\n")
      .append("Original Error:\n")
      .append(e.getMessage());

    VcsImplUtil.showErrorMessage(
      myProject,
      message.toString(),
      HgVcsMessages.message("hg4idea.error")
    );
  }

  private static class WarningReceiver extends SocketServer.Protocol{
    private StringBuffer warnings = new StringBuffer();

    public boolean handleConnection(Socket socket) throws IOException {
      DataInputStream dataInput = new DataInputStream(socket.getInputStream());

      int numOfWarnings = dataInput.readInt();
      for (int i = 0; i < numOfWarnings; i++) {
        warnings.append(new String(readDataBlock(dataInput)));
      }
      return true;
    }


    public String getWarnings() {
      return warnings.toString();
    }
  }

  private static class PromptReceiver extends SocketServer.Protocol {
    @Nullable HgPromptHandler myHandler;

    public PromptReceiver(@Nullable HgPromptHandler handler) {
      myHandler = handler;
    }

    public boolean handleConnection(Socket socket) throws IOException {
      DataInputStream dataInput = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      final String message = new String(readDataBlock(dataInput));
      int numOfChoices = dataInput.readInt();
      final HgPromptChoice[] choices = new HgPromptChoice[numOfChoices];
      for (int i = 0; i < numOfChoices; i++) {
        String choice = new String(readDataBlock(dataInput));
        choices[i] = new HgPromptChoice(i, choice);
      }
      int defaultChoiceInt = dataInput.readInt();
      final HgPromptChoice defaultChoice = choices[defaultChoiceInt];
      if (myHandler != null && myHandler.shouldHandle(message)) {
        int chosen = myHandler.promptUser(message, choices, defaultChoice).getChosenIndex();
        sendChoiceToHg(out, chosen);
        return true;
      }
      final int[] index = new int[]{-1};
      try {
        EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            String[] choicePresentationArray = new String[choices.length];
            for (int i = 0; i < choices.length; ++i) {
              choicePresentationArray[i] = choices[i].toString();
            }
            index[0] = Messages
              .showDialog(message, "hg4idea",
                          choicePresentationArray,
                          defaultChoice.getChosenIndex(), Messages.getQuestionIcon());
          }
        });

        int chosen = index[0];
        sendChoiceToHg(out, chosen);
        return true;
      }
      catch (InterruptedException e) {
        //do nothing
        return true;
      }
      catch (InvocationTargetException e) {
        //shouldn't happen
        throw new RuntimeException(e);
      }
    }

    private static void sendChoiceToHg(@NotNull DataOutputStream outStream, int choice) throws IOException {
      if (choice == HgPromptChoice.CLOSED_OPTION) {
        outStream.writeInt(-1);
      }
      else {
        outStream.writeInt(choice);
      }
    }
  }

  private static class PassReceiver extends SocketServer.Protocol{
    private final Project myProject;
    private HgCommandAuthenticator myAuthenticator;
    private boolean myForceAuthorization;

    private PassReceiver(Project project, boolean forceAuthorization) {
      myProject = project;
      myForceAuthorization = forceAuthorization;
    }

    @Override
    public boolean handleConnection(Socket socket) throws IOException {
      DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());

      String command = new String(readDataBlock(dataInputStream));
      assert "getpass".equals(command) : "Invalid command: " + command;
      String uri = new String(readDataBlock(dataInputStream));
      String path = new String(readDataBlock(dataInputStream));
      String proposedLogin = new String(readDataBlock(dataInputStream));

      HgCommandAuthenticator authenticator = new HgCommandAuthenticator(myProject, myForceAuthorization);
      boolean ok = authenticator.promptForAuthentication(myProject, proposedLogin, uri, path);
      if (ok) {
        myAuthenticator = authenticator;
        sendDataBlock(out, authenticator.getUserName().getBytes());
        sendDataBlock(out, authenticator.getPassword().getBytes());
      }
      return true;
    }

    public void saveCredentials() {
      if (myAuthenticator == null) return;
      myAuthenticator.saveCredentials();
    }
  }
}
