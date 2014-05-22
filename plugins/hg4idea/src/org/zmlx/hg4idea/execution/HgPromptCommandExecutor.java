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
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class HgPromptCommandExecutor extends HgCommandExecutor {

  public HgPromptCommandExecutor(@NotNull Project project) {
    super(project);
  }

  @Override
  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo,
                                                @NotNull final String operation,
                                                @Nullable final List<String> arguments) {

    final List<String> cmdLine = new LinkedList<String>();
    WarningReceiver warningReceiver = new WarningReceiver();
    SocketServer promptServer = new SocketServer(new PromptReceiver(new HgDeleteModifyPromptHandler()));
    SocketServer warningServer = new SocketServer(warningReceiver);


    try {
      int promptPort = promptServer.start();
      int warningPort = warningServer.start();
      cmdLine.add("--config");
      cmdLine.add("extensions.hg4ideapromptextension=" + myVcs.getPromptHooksExtensionFile().getAbsolutePath());
      cmdLine.add("--config");
      cmdLine.add("hg4ideaprompt.port=" + promptPort);
      cmdLine.add("--config");
      cmdLine.add("hg4ideawarn.port=" + warningPort);
    }
    catch (IOException e) {
      showError(e);
      LOG.info("IOException during preparing command", e);
      promptServer.stop();
      warningServer.stop();
      return null;
    }

    if (arguments != null && arguments.size() != 0) {
      cmdLine.addAll(arguments);
    }

    HgCommandResult result = super.executeInCurrentThread(repo, operation, cmdLine);
    promptServer.stop();
    warningServer.stop();
    String warnings = warningReceiver.getWarnings();
    result.setWarnings(warnings);
    return result;
  }

  private static class WarningReceiver extends SocketServer.Protocol {
    private StringBuffer warnings = new StringBuffer();

    public boolean handleConnection(Socket socket) throws IOException {
      //noinspection IOResourceOpenedButNotSafelyClosed
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
      //noinspection IOResourceOpenedButNotSafelyClosed
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
              .showDialog(message, "Hg4idea",
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
}
