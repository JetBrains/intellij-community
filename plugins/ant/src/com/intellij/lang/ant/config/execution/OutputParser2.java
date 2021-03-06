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
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.segments.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.rt.ant.execution.IdeaAntLogger2;
import com.intellij.rt.ant.execution.PacketProcessor;
import org.jetbrains.annotations.Nls;

import java.io.IOException;

final class OutputParser2 extends OutputParser implements PacketProcessor, InputConsumer, OutputPacketProcessor {
  private static final Logger LOG = Logger.getInstance(OutputParser2.class);
  private int myLastPacketIndex = -1;

  private OutputParser2(Project project,
                        OSProcessHandler processHandler,
                        AntBuildMessageView errorsView,
                        ProgressIndicator progress,
                        @Nls String buildName) {
    super(project, processHandler, errorsView, progress, buildName);
  }

  @Override
  public void processOutput(Printable printable) {
    printable.printOn(null);
  }

  @Override
  public void processPacket(String packet) {
    SegmentReader reader = new SegmentReader(packet);
    int index = reader.readInt();
    if (myLastPacketIndex + 1 < index) {
      LOG.error("last: " + myLastPacketIndex + " current: " + index);
    }
    if (myLastPacketIndex + 1 > index) return;
    myLastPacketIndex++;
    char id = reader.readChar();
    if (id == IdeaAntLogger2.INPUT_REQUEST) {
      try {
        InputRequestHandler.processInput(getProject(), reader, getProcessHandler());
      }
      catch (IOException e) {
        MessagesEx.error(getProject(), e.getMessage()).showLater();
      }
    }
    else {
      int priority = fixPriority(reader.readInt());
      char contentType = reader.readChar();
      String message = reader.readLimitedString();
      if (id == IdeaAntLogger2.BUILD_END) {
        if (contentType == IdeaAntLogger2.EXCEPTION_CONTENT) {
          processTag(IdeaAntLogger2.EXCEPTION, message, priority);
        }
      }
      else {
        processTag(id, message, priority);
      }
    }
  }

  @Override
  public void onOutput(@NlsSafe String text, ConsoleViewContentType contentType) {
    if (text.isEmpty()) return;
    if (myLastPacketIndex != -1) return;
    if (contentType == ConsoleViewContentType.ERROR_OUTPUT) readErrorOutput(text);
  }

  public static OutputParser attachParser(final Project myProject,
                                          AntProcessHandler handler,
                                          final AntBuildMessageView errorView,
                                          final ProgressIndicator progress,
                                          final AntBuildFile buildFile) {
    final OutputParser2 parser = new OutputParser2(myProject, handler, errorView, progress, buildFile.getName());
    final DeferredActionsQueue queue = new DeferredActionsQueueImpl();
    handler.getErr().setPacketDispatcher(parser, queue);
    return parser;
  }
}
