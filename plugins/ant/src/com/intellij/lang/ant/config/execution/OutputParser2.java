package com.intellij.lang.ant.config.execution;

import com.intellij.execution.junit.JUnitProcessHandler;
import com.intellij.execution.junit2.segments.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.rt.ant.execution.IdeaAntLogger2;
import com.intellij.rt.execution.junit.segments.PacketProcessor;

import java.io.IOException;

final class OutputParser2 extends OutputParser implements PacketProcessor, InputConsumer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.OutputParser2");
  private int myLastPacketIndex = -1;

  private OutputParser2(Project project,
                        OSProcessHandler processHandler,
                        AntBuildMessageView errorsView,
                        BuildProgressWindow progress,
                        String buildName) {
    super(project, processHandler, errorsView, progress, buildName);
  }

  public void processPacket(String packet) {
    SegmentReader reader = new SegmentReader(packet);
    int index = reader.readInt();
    if (myLastPacketIndex + 1 < index) {
      LOG.assertTrue(false, "last: " + myLastPacketIndex + " current: " + index);
    }
    if (myLastPacketIndex + 1 > index) return;
    myLastPacketIndex++;
    char id = reader.readChar();
    if (id == IdeaAntLogger2.INPUT_REQUEST) {
      try {
        InputRequestHandler.processInput(getProject(), reader, getProcessHandler());
      }
      catch (IOException e) {
        MessagesEx.error(getProject(), e.getMessage());
      }
    }
    else {
      int priority = reader.readInt();
      char contentType = reader.readChar();
      String message = reader.readLimitedString();
      switch (id) {
        case IdeaAntLogger2.BUILD_END:
          if (contentType == IdeaAntLogger2.EXCEPTION_CONTENT) {
            processTag(IdeaAntLogger2.EXCEPTION, message, priority);
          }
          break;
        default:
          processTag(id, message, priority);
      }
    }
  }

  public void onOutput(String text, ConsoleViewContentType contentType) {
    if (text.length() == 0) return;
    if (myLastPacketIndex != -1) return;
    if (contentType == ConsoleViewContentType.ERROR_OUTPUT) readErrorOutput(text);
  }

  public static OutputParser attachParser(final Project myProject,
                                          JUnitProcessHandler handler,
                                          final AntBuildMessageView errorView,
                                          final BuildProgressWindow progress,
                                          final AntBuildFile buildFile) {
    OutputParser2 parser = new OutputParser2(myProject, handler, errorView, progress, buildFile.getAntFile().getName());
    DeferedActionsQueue queue = new DeferedActionsQueueImpl();
    attach(parser, handler.getOut(), queue);
    attach(parser, handler.getErr(), queue);
    return parser;
  }

  private static void attach(OutputParser2 parser, PacketExtractorBase packetExtractorBase, DeferedActionsQueue queue) {
    packetExtractorBase.setFulfilledWorkGate(queue);
    packetExtractorBase.setPacketProcessor(parser);
  }
}
