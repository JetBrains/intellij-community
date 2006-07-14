package com.intellij.lang.ant.config.execution;

import com.intellij.execution.junit2.segments.SegmentReader;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

class InputRequestHandler {

  private InputRequestHandler() {
  }

  public static void processInput(Project project, SegmentReader reader, OSProcessHandler handler) throws IOException {
    String input = askUser(reader, project);
    Charset charset = handler.getCharset();
    OutputStream outputStream = handler.getProcessInput();
    sendInput(input, charset, outputStream);
  }

  private static void sendInput(String input, Charset charset, OutputStream outputStream) throws IOException {
    byte[] bytes = input.getBytes(charset.name());
    int length = bytes.length;
    byte[] packet = new byte[length + 4];
    System.arraycopy(bytes, 0, packet, 4, length);
    packet[0] = (byte)(length >> 24);
    packet[1] = (byte)(length >> 16);
    packet[2] = (byte)(length >> 8);
    packet[3] = (byte)length;
    outputStream.write(packet);
    outputStream.flush();
  }

  private static String askUser(SegmentReader reader, Project project) {
    String prompt = reader.readLimitedString();
    String[] choices = reader.readStringArray();
    MessagesEx.BaseInputInfo question;
    if (choices.length == 0) {
      question = new MessagesEx.InputInfo(project);
    }
    else {
      MessagesEx.ChoiceInfo choiceInfo = new MessagesEx.ChoiceInfo(project);
      choiceInfo.setChoices(choices, 0);
      question = choiceInfo;
    }
    question.setIcon(Messages.getQuestionIcon());
    question.setTitle(AntBundle.message("user.inout.request.ant.build.input.dialog.title"));
    question.setMessage(prompt);
    question.setIcon(Messages.getQuestionIcon());
    return question.forceUserInput();
  }
}

