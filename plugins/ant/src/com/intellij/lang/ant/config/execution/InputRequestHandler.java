/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.segments.SegmentReader;
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
    String defaultValue = reader.readLimitedString();
    String[] choices = reader.readStringArray();
    MessagesEx.BaseInputInfo question;
    if (choices.length == 0) {
      MessagesEx.InputInfo inputInfo = new MessagesEx.InputInfo(project);
      inputInfo.setDefaultValue(defaultValue);
      question = inputInfo;
    }
    else {
      MessagesEx.ChoiceInfo choiceInfo = new MessagesEx.ChoiceInfo(project);
      choiceInfo.setChoices(choices, defaultValue);
      question = choiceInfo;
    }
    question.setIcon(Messages.getQuestionIcon());
    question.setTitle(AntBundle.message("user.inout.request.ant.build.input.dialog.title"));
    question.setMessage(prompt);
    question.setIcon(Messages.getQuestionIcon());
    return question.forceUserInput();
  }
}

