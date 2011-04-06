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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public final class HgCommandExecutor {
  
  public static final List<String> DEFAULT_OPTIONS = Arrays.asList(
    "--config", "ui.merge=internal:merge"
  );

  private static final Logger LOG = Logger.getInstance(HgCommandExecutor.class.getName());

  private final Project myProject;
  private final HgGlobalSettings mySettings;
  private final HgExecutableValidator myValidator;
  private final HgVcs myVcs;

  private Charset myCharset;
  private boolean myIsSilent;
  private List<String> myOptions = DEFAULT_OPTIONS;

  public HgCommandExecutor(Project project, HgGlobalSettings settings) {
    myProject = project;
    mySettings = settings;
    myVcs = HgVcs.getInstance(myProject);
    myValidator = myVcs.getExecutableValidator();
    myCharset = Charset.defaultCharset();
    myIsSilent = false;
  }

  public void setCharset(Charset charset) {
    myCharset = charset;
  }

  public void setSilent(boolean isSilent) {
    myIsSilent = isSilent;
  }

  @Deprecated
  public static HgCommandExecutor getInstance(Project project) {
    return new HgCommandExecutor(project, HgVcs.getInstance(project).getGlobalSettings());
  }

  @Nullable
  public HgCommandResult execute(VirtualFile repo, String operation, List<String> arguments) {
    return execute(
      repo, DEFAULT_OPTIONS, operation, arguments, Charset.defaultCharset(), false
    );
  }

  @Nullable
  public HgCommandResult execute(final VirtualFile repo, final List<String> hgOptions, final String operation, final List<String> arguments, final Charset charset, final boolean silent) {
      return executeOffOfEDT(repo, hgOptions, operation, arguments, charset, silent);
  }

  private HgCommandResult executeOffOfEDT(VirtualFile repo,
                                          List<String> hgOptions,
                                          String operation,
                                          List<String> arguments,
                                          Charset charset,
                                          boolean silent) {
//    assert !EventQueue.isDispatchThread();
    if (myProject.isDisposed()) {
      return null;
    }

    final List<String> cmdLine = new LinkedList<String>();
    cmdLine.add(myVcs.getHgExecutable());
    if (repo != null) {
      cmdLine.add("--repository");
      cmdLine.add(repo.getPath());
    }

    WarningReceiver warningReceiver = new WarningReceiver();
    PassReceiver passReceiver = new PassReceiver(myProject);
    
    SocketServer promptServer = new SocketServer(new PromptReceiver());
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
      return null;
    }
    cmdLine.addAll(hgOptions);
    cmdLine.add(operation);
    if (arguments != null && arguments.size() != 0) {
      cmdLine.addAll(arguments);
    }
    ShellCommand shellCommand = new ShellCommand(mySettings.isRunViaBash());
    HgCommandResult result;
    try {
      String workingDir = repo != null ? repo.getPath() : null;
      result = shellCommand.execute(cmdLine, workingDir, charset);
      if (!HgErrorUtil.isAuthorizationError(result)) {
        passReceiver.saveCredentials();
      }
    } catch (ShellCommandException e) {
      if (!silent) {
        if (myValidator.checkExecutableAndNotifyIfNeeded()) {
          // if the problem was not with invalid executable - show error.
          showError(e);
          LOG.info(e.getMessage(), e);
        }
      } else {
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

    // logging to the Version Control console (without extensions and configs)
    final String cmdString = String.format("%s %s %s", mySettings.isRunViaBash() ? "bash -c " + HgVcs.HG_EXECUTABLE_FILE_NAME : HgVcs.HG_EXECUTABLE_FILE_NAME, operation,
            StringUtils.join(maskAuthInfoFromUrl(arguments), " "));
    myVcs.showMessageInConsole(cmdString, ConsoleViewContentType.NORMAL_OUTPUT.getAttributes());
    LOG.info(cmdString);
    if (!silent) {
      myVcs.showMessageInConsole(result.getRawOutput(), ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
      LOG.info(result.getRawOutput());
    }
    myVcs.showMessageInConsole(result.getRawError(), ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
    LOG.info(result.getRawError());

    return result;
  }

  /**
   * Strips possible authentication information from arguments passed to the command line
   * to prevent private information appear in the VCS console or logs.
   * @param arguments command line arguments.
   * @return command line arguments which don't contain authentication information.
   */
  private static List<String> maskAuthInfoFromUrl(List<String> arguments) {
    if (arguments == null || arguments.isEmpty()) {
      return arguments;
    }
    final List<String> newArgs = new ArrayList<String>(arguments.size());
    for (String arg : arguments) {
      if (!arg.contains("@")) { // simple filter
        newArgs.add(arg);
      } else {
        try {
          final URI uri = new URI(arg); // parsing via URI methods, exception means it's not an URI
          newArgs.add(uri.toString().replace(uri.getUserInfo(), "<username>:<password>"));
        } catch (Throwable e) {
          newArgs.add(arg);
        }
      }
    }
    return newArgs;
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

    VcsUtil.showErrorMessage(
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

    public boolean handleConnection(Socket socket) throws IOException {
      DataInputStream dataInput = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      final String message = new String(readDataBlock(dataInput));
      int numOfChoices = dataInput.readInt();
      final Choice[] choices = new Choice[numOfChoices];
      for (int i = 0; i < numOfChoices; i++) {
        String choice = new String(readDataBlock(dataInput));
        choices[i] = new Choice(choice);
      }
      int defaultChoiceInt = dataInput.readInt();
      final Choice defaultChoice = choices[defaultChoiceInt];

      final int[] index = new int[]{-1};
      try {
        EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            Window parent = ApplicationManager.getApplication().getComponent(Window.class);
            index[0] = JOptionPane
              .showOptionDialog(parent, message, "hg4idea", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices,
                                defaultChoice);
          }
        });
        
        int chosen = index[0];
        if (chosen == JOptionPane.CLOSED_OPTION) {
          out.writeInt(-1);
        } else {
          out.writeInt(chosen);
        }
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

    private static class Choice{
      private final String fullString;
      private final String representation;
      private final String choiceChar;

      private Choice(String fullString) {
        this.fullString = fullString;
        this.representation = fullString.replaceAll("&", "");
        int index = fullString.indexOf("&");
        this.choiceChar = "" + fullString.charAt(index + 1);
        
      }

      @Override
      public String toString() {
        return representation;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Choice choice = (Choice) o;

        if (!fullString.equals(choice.fullString)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        return fullString.hashCode();
      }
    }
  }
  
  private static class PassReceiver extends SocketServer.Protocol{
    private final Project myProject;
    private HgCommandAuthenticator myAuthenticator;

    private PassReceiver(Project project) {
      myProject = project;
    }

    @Override
    public boolean handleConnection(Socket socket) throws IOException {
      DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());

      String command = new String(readDataBlock(dataInputStream));
      assert "getpass".equals(command);
      String uri = new String(readDataBlock(dataInputStream));
      String path = new String(readDataBlock(dataInputStream));
      String proposedLogin = new String(readDataBlock(dataInputStream));

      HgCommandAuthenticator authenticator = new HgCommandAuthenticator(myProject);
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
