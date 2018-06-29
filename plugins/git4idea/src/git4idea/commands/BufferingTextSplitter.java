// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

class BufferingTextSplitter {
  @NotNull private final StringBuilder myLineBuffer = new StringBuilder();
  @NotNull private final Consumer<String> myLineConsumer;
  private boolean myCrLast = false;

  public BufferingTextSplitter(@NotNull Consumer<String> lineConsumer) {myLineConsumer = lineConsumer;}

  /**
   * Walks the input array from 0 to {@param contentLength - 1} and sends complete lines (separated by \n,\r or \r\n) to the consumer
   */
  public void process(char[] input, int contentLength) {
    for (int i = 0; i < contentLength; i++) {
      char character = input[i];
      switch (character) {
        case '\n':
          myLineBuffer.append(character);
          sendLine();
          break;
        case '\r':
          if (myCrLast) sendLine();
          myLineBuffer.append(character);
          myCrLast = true;
          break;
        default:
          if (myCrLast) sendLine();
          myLineBuffer.append(character);
          break;
      }
    }
  }

  /**
   * Flush incomplete lines buffer to consumer
   */
  public void flush() {
    if (myLineBuffer.length() > 0) {
      sendLine();
    }
  }

  private void sendLine() {
    String text = myLineBuffer.toString();
    myLineBuffer.setLength(0);
    myCrLast = false;
    myLineConsumer.accept(text);
  }
}