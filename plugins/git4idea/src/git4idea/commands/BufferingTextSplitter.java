// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

class BufferingTextSplitter {
  @NotNull private final StringBuilder myLineBuffer = new StringBuilder();
  @NotNull private final Consumer<String> myLineConsumer;

  public BufferingTextSplitter(@NotNull Consumer<String> lineConsumer) {myLineConsumer = lineConsumer;}

  /**
   * Walks the input array from 0 to {@param contentLength - 1} and sends complete lines (separated by \n,\r or \r\n) to the consumer
   */
  public void process(char[] input, int contentLength) {
    boolean crLast = false;
    for (int i = 0; i < contentLength; i++) {
      char character = input[i];
      switch (character) {
        case '\n':
          myLineBuffer.append(character);
          crLast = false;
          sendLine();
          break;
        case '\r':
          myLineBuffer.append(character);
          crLast = true;
          break;
        default:
          if (crLast) {
            sendLine();
            crLast = false;
          }
          myLineBuffer.append(character);
          break;
      }
    }
    if (crLast) sendLine();
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
    myLineConsumer.accept(text);
  }
}