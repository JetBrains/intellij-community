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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public abstract class OutputLineSplitter {
  private static final String TEAMCITY_SERVICE_MESSAGE_PREFIX = "##teamcity[";

  private final boolean myStdinSupportEnabled;

  private final Map<Key, StringBuilder> myBuffers = new THashMap<>();
  private final List<OutputChunk> myStdOutChunks = new ArrayList<>();

  public OutputLineSplitter(boolean stdinEnabled) {
    myBuffers.put(ProcessOutputTypes.SYSTEM, new StringBuilder());
    myBuffers.put(ProcessOutputTypes.STDERR, new StringBuilder());

    myStdinSupportEnabled = stdinEnabled;
  }

  public void process(final String text, final Key outputType) {
    int from = 0;
    int to = 0;
    for (; to < text.length(); to++) {
      if (text.charAt(to) == '\n') {
        processLine(text.substring(from, to + 1), outputType);
        from = to + 1;
      }
    }
    if (from < text.length()) {
      processLine(text.substring(from), outputType);
    }
  }

  private void processLine(String text, Key outputType) {
    if (!myBuffers.keySet().contains(outputType)) {
      processStdOutConsistently(text, outputType);
    }
    else {
      StringBuilder buffer = myBuffers.get(outputType);
      if (!text.endsWith("\n")) {
        buffer.append(text);
        return;
      }
      if (buffer.length() > 0) {
        buffer.append(text);
        text = buffer.toString();
        buffer.setLength(0);
      }

      onLineAvailable(text, outputType, false);
    }
  }

  private void processStdOutConsistently(final String text, final Key outputType) {
    final int textLength = text.length();
    if (textLength == 0) {
      return;
    }

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
      if (myStdinSupportEnabled && !isMostLikelyServiceMessagePart(text)) {
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
    final boolean isTCLikeFakeOutput = chunks.size() == 1;
    for (OutputChunk chunk : chunks) {
      onLineAvailable(chunk.getText(), chunk.getKey(), isTCLikeFakeOutput);
    }
  }


  public void flush() {
    flushStdOutBuffer();

    for (Map.Entry<Key, StringBuilder> each : myBuffers.entrySet()) {
      StringBuilder buffer = each.getValue();
      if (buffer.length() > 0) {
        onLineAvailable(buffer.toString(), each.getKey(), false);
        buffer.setLength(0);
      }
    }
  }

  protected boolean isMostLikelyServiceMessagePart(@NotNull final String text) {
    return text.startsWith(TEAMCITY_SERVICE_MESSAGE_PREFIX);
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
