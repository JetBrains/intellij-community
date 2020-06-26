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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HgRemoteCommandExecutor extends HgCommandExecutor {

  @Nullable private final ModalityState myState;
  final boolean myIgnoreAuthorizationRequest;

  public HgRemoteCommandExecutor(@NotNull Project project, @Nullable String destination) {
    this(project, destination, null, false);
  }

  public HgRemoteCommandExecutor(@NotNull Project project,
                                 @Nullable String destination,
                                 @Nullable ModalityState state, boolean ignoreAuthorizationRequest) {
    super(project, destination);
    myState = state;
    myIgnoreAuthorizationRequest = ignoreAuthorizationRequest;
  }

  @Override
  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo, @NotNull final String operation,
                                                @Nullable final List<String> arguments) {


    HgCommandResult result = executeRemoteCommandInCurrentThread(repo, operation, arguments, false);
    if (!myIgnoreAuthorizationRequest && HgErrorUtil.isAuthorizationError(result)) {
      if (HgErrorUtil.hasAuthorizationInDestinationPath(myDestination)) {
        new HgCommandResultNotifier(myProject)
          .notifyError(result, HgBundle.message("hg4idea.command.executor.remote.auth.failed"),
                       HgBundle.message("hg4idea.command.executor.remote.auth.failed.msg"));
        return null;
      }
      result = executeRemoteCommandInCurrentThread(repo, operation, arguments, true);
    }
    return result;
  }

  @Nullable
  private HgCommandResult executeRemoteCommandInCurrentThread(@Nullable final VirtualFile repo,
                                                              @NotNull final String operation,
                                                              @Nullable final List<String> arguments,
                                                              boolean forceAuthorization) {

    PassReceiver passReceiver = new PassReceiver(myProject, forceAuthorization, myIgnoreAuthorizationRequest, myState);
    SocketServer passServer = new SocketServer(passReceiver);
    try {
      int passPort = passServer.start();
      HgCommandResult result = super.executeInCurrentThread(repo, operation, prepareArguments(arguments, passPort));
      if (HgErrorUtil.isAuthorizationError(result)) {
        passReceiver.forgetPassword();
      }
      return result;
    }
    catch (IOException e) {
      showError(e);
      LOG.info("IOException during preparing command", e);
      return null;
    }
    finally {
      passServer.stop();
    }
  }

  private List<String> prepareArguments(List<String> arguments, int port) {
    List<String> cmdArguments = new ArrayList<>();
    cmdArguments.add("--config");
    cmdArguments.add("extensions.hg4ideapromptextension=" + myVcs.getPromptHooksExtensionFile().getAbsolutePath());
    cmdArguments.add("--config");
    cmdArguments.add("hg4ideapass.port=" + port);

    if (arguments != null && arguments.size() != 0) {
      cmdArguments.addAll(arguments);
    }
    return cmdArguments;
  }

  @Override
  protected void logCommand(@NotNull String operation, @Nullable List<String> arguments) {
    //do not log arguments for remote command because of internal password port info etc
    super.logCommand(operation, null);
  }

  private static final class PassReceiver extends SocketServer.Protocol {
    private final Project myProject;
    private HgCommandAuthenticator myAuthenticator;
    private final boolean myForceAuthorization;
    private final boolean mySilentMode;
    @Nullable private final ModalityState myState;

    private PassReceiver(Project project, boolean forceAuthorization, boolean silent, @Nullable ModalityState state) {
      myProject = project;
      myForceAuthorization = forceAuthorization;
      mySilentMode = silent;
      myState = state;
    }

    @Override
    public boolean handleConnection(Socket socket) throws IOException {
      //noinspection IOResourceOpenedButNotSafelyClosed
      DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());

      String command = new String(readDataBlock(dataInputStream), StandardCharsets.UTF_8);
      assert "getpass".equals(command) : "Invalid command: " + command;
      String uri = new String(readDataBlock(dataInputStream), StandardCharsets.UTF_8);
      String path = new String(readDataBlock(dataInputStream), StandardCharsets.UTF_8);
      String proposedLogin = new String(readDataBlock(dataInputStream), StandardCharsets.UTF_8);

      HgCommandAuthenticator authenticator = new HgCommandAuthenticator(myForceAuthorization, mySilentMode);
      boolean ok = authenticator.promptForAuthentication(myProject, proposedLogin, uri, path, myState);
      if (ok) {
        myAuthenticator = authenticator;
        sendDataBlock(out, authenticator.getUserName().getBytes(StandardCharsets.UTF_8));
        sendDataBlock(out, authenticator.getPassword().getBytes(StandardCharsets.UTF_8));
      }
      return true;
    }

    public void forgetPassword() {
      if (myAuthenticator == null) return;
      myAuthenticator.forgetPassword();
    }
  }
}
