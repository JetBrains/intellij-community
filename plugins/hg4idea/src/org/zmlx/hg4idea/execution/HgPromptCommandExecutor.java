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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
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
    SocketServer promptServer = new SocketServer(new PromptReceiver(new HgDeleteModifyPromptHandler()));
    try {
      int promptPort = promptServer.start();
      return super.executeInCurrentThread(repo, operation, prepareArguments(arguments, promptPort));
    }
    catch (IOException e) {
      showError(e);
      LOG.info("IOException during preparing command", e);
      return null;
    }
    finally {
      promptServer.stop();
    }
  }

  private List<String> prepareArguments(List<String> arguments, int port) {
    List<String> cmdArguments = ContainerUtil.newArrayList();
    cmdArguments.add("--config");
    cmdArguments.add("extensions.hg4ideapromptextension=" + myVcs.getPromptHooksExtensionFile().getAbsolutePath());
    cmdArguments.add("--config");
    cmdArguments.add("hg4ideaprompt.port=" + port);

    if (arguments != null && arguments.size() != 0) {
      cmdArguments.addAll(arguments);
    }
    return cmdArguments;
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
              .showDialog(message, "Mercurial Prompt Message",
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
