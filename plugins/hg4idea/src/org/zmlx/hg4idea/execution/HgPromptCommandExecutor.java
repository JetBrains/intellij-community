// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    List<String> cmdArguments = new ArrayList<>();
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

    PromptReceiver(@Nullable HgPromptHandler handler) {
      myHandler = handler;
    }

    @Override
    public boolean handleConnection(Socket socket) throws IOException {
      //noinspection IOResourceOpenedButNotSafelyClosed
      DataInputStream dataInput = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      final String message = new String(readDataBlock(dataInput), StandardCharsets.UTF_8); //NON-NLS
      int numOfChoices = dataInput.readInt();
      final HgPromptChoice[] choices = new HgPromptChoice[numOfChoices];
      for (int i = 0; i < numOfChoices; i++) {
        String choice = new String(readDataBlock(dataInput), StandardCharsets.UTF_8);
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
        EventQueue.invokeAndWait(() -> {
          String[] choicePresentationArray = new String[choices.length];
          for (int i = 0; i < choices.length; ++i) {
            choicePresentationArray[i] = choices[i].toString();
          }
          index[0] = Messages
            .showDialog(message, HgBundle.message("hg4idea.prompt.message"),
                        choicePresentationArray,
                        defaultChoice.getChosenIndex(), Messages.getQuestionIcon());
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
