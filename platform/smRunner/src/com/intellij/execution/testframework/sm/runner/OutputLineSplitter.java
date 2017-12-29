/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public abstract class OutputLineSplitter {
  private static final String TEAMCITY_SERVICE_MESSAGE_PREFIX = ServiceMessage.SERVICE_MESSAGE_START;
  private static final char NEW_LINE = '\n';

  private final boolean myStdinSupportEnabled;

  private final List<OutputChunk> myStdOutChunks = new ArrayList<>();
  private final List<OutputChunk> myStdErrChunks = new ArrayList<>();
  private final List<OutputChunk> mySystemChunks = new ArrayList<>();

  public OutputLineSplitter(boolean stdinEnabled) {
    myStdinSupportEnabled = stdinEnabled;
  }

  public void process(@NotNull String text, @NotNull Key outputType) {
    int from = 0;
    // new line char and teamcity message start are two reasons to flush previous line
    int newLineInd = text.indexOf(NEW_LINE);
    int teamcityMessageStartInd = text.indexOf(TEAMCITY_SERVICE_MESSAGE_PREFIX);
    while (from < text.length()) {
      int nextFrom = Math.min(newLineInd != -1 ? newLineInd + 1 : text.length(),
                              teamcityMessageStartInd != -1 ? teamcityMessageStartInd : text.length());
      String chunk = text.substring(from, nextFrom);
      processLine(chunk, outputType);
      from = nextFrom;
      if (nextFrom == teamcityMessageStartInd) {
        flush(); // Message may still go to buffer if it does not end with new line, force flush
        teamcityMessageStartInd = text.indexOf(TEAMCITY_SERVICE_MESSAGE_PREFIX, nextFrom + TEAMCITY_SERVICE_MESSAGE_PREFIX.length());
      }
      if (newLineInd != -1 && nextFrom == newLineInd + 1) {
        newLineInd = text.indexOf(NEW_LINE, nextFrom);
      }
    }
  }

  private void processLine(@NotNull String text, @NotNull Key outputType) {
    if (text.isEmpty()) {
      return;
    }
    if (ProcessOutputType.isStdout(outputType)) {
      processStdOutConsistently(text, outputType);
    }
    else {
      List<OutputChunk> chunksToFlush = null;
      List<OutputChunk> chunks = outputType == ProcessOutputTypes.SYSTEM ? mySystemChunks : myStdErrChunks;
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (chunks) {
        OutputChunk lastChunk = ContainerUtil.getLastItem(chunks);
        if (lastChunk != null && outputType.equals(lastChunk.getKey())) {
          lastChunk.append(text);
        }
        else {
          chunks.add(new OutputChunk(outputType, text));
        }
        if (StringUtil.endsWithChar(text, NEW_LINE)) {
          chunksToFlush = new ArrayList<>(chunks);
          chunks.clear();
        }
      }
      if (chunksToFlush != null) {
        onChunksAvailable(chunksToFlush, false);
      }
    }
  }

  private void processStdOutConsistently(final String text, final Key outputType) {
    final int textLength = text.length();

    synchronized (myStdOutChunks) {
      myStdOutChunks.add(new OutputChunk(outputType, text));
    }

    final char lastChar = text.charAt(textLength - 1);
    if (lastChar == '\n' || lastChar == '\r') {
      // buffer contains consistent string
      flushStdOutBuffer();
    }
    else {
      // test framework may show some promt and ask user for smth. Question may not
      // finish with \n or \r thus buffer wont be flushed and user will have to input smth
      // before question. And question will became visible with next portion of text.
      // Such behaviour is confusing. So
      // 1. Let's assume that sevice messages starts with \n if console is editable
      // 2. Then we can suggest that each service message will start from new line and buffer should
      //    be flushed before every service message. Thus if chunks list is empty and output doesn't end
      //    with \n or \r but starts with ##teamcity then it is a service message and should be buffered otherwise
      //    we can safely flush buffer.

      // TODO if editable:
      if (myStdinSupportEnabled && !isInTeamcityMessage()) {
        // We should not flush in the middle of TC message because of [PY-7659]
        flushStdOutBuffer();
      }
    }
  }

  private void flushStdOutBuffer() {
    // if osColoredProcessHandler was attached it can split string with several colors
    // in several  parts. Thus '\n' symbol may be send as one part with some color
    // such situation should differ from single '\n' from process that is used by TC reporters
    // to separate TC commands from other stuff + optimize flushing
    // TODO: probably in IDEA mode such runners shouldn't add explicit \n because we can
    // successfully process broken messages across several flushes
    // size of parts may tell us either \n was single in original flushed data or it was
    // separated by process handler
    List<OutputChunk> chunks = new ArrayList<>();
    OutputChunk lastChunk = null;
    synchronized (myStdOutChunks) {
      for (OutputChunk chunk : myStdOutChunks) {
        if (lastChunk != null && chunk.getKey() == lastChunk.getKey()) {
          lastChunk.append(chunk.getText());
        }
        else {
          lastChunk = chunk;
          chunks.add(chunk);
        }
      }

      myStdOutChunks.clear();
    }
    onChunksAvailable(chunks, chunks.size() == 1);
  }

  public void flush() {
    flushStdOutBuffer();

    List<OutputChunk> stderrChunksToFlush;
    synchronized (myStdErrChunks) {
      stderrChunksToFlush = new ArrayList<>(myStdErrChunks);
      myStdErrChunks.clear();
    }
    onChunksAvailable(stderrChunksToFlush, false);

    List<OutputChunk> systemChunksToFlush;
    synchronized (mySystemChunks) {
      systemChunksToFlush = new ArrayList<>(mySystemChunks);
      mySystemChunks.clear();
    }
    onChunksAvailable(systemChunksToFlush, false);
  }

  private void onChunksAvailable(@NotNull List<OutputChunk> chunks, boolean tcLikeFakeOutput) {
    for (OutputChunk chunk : chunks) {
      onLineAvailable(chunk.getText(), chunk.getKey(), tcLikeFakeOutput);
    }
  }

  /**
   * @return if current stdout cache contains part of TC message.
   */
  protected boolean isInTeamcityMessage() {
    return myStdOutChunks.stream().anyMatch(chunk -> chunk.getText().startsWith(TEAMCITY_SERVICE_MESSAGE_PREFIX));
  }

  protected abstract void onLineAvailable(@NotNull String text, @NotNull Key outputType, boolean tcLikeFakeOutput);

  private static class OutputChunk {
    private final Key myKey;
    private String myText;
    private StringBuilder myBuilder;

    private OutputChunk(Key key, String text) {
      myKey = key;
      myText = text;
    }

    public Key getKey() {
      return myKey;
    }

    public String getText() {
      if (myBuilder != null) {
        myText = myBuilder.toString();
        myBuilder = null;
      }
      return myText;
    }

    public void append(String text) {
      if (myBuilder == null) {
        myBuilder = new StringBuilder(myText);
        myText = null;
      }
      myBuilder.append(text);
    }
  }
}
