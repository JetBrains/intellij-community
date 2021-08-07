// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

class BufferingTextSplitter {
  @NotNull private final StringBuilder myLineBuffer = new StringBuilder();
  private boolean myBufferedCr = false;

  @NotNull private final LineConsumer myLineConsumer;

  BufferingTextSplitter(@NotNull LineConsumer lineConsumer) {
    myLineConsumer = lineConsumer;
  }

  /**
   * Walks the input array from 0 to {@param contentLength - 1} and sends complete lines (separated by \n,\r or \r\n) to the consumer
   */
  public void process(char[] input, int contentLength) {
    int offset = 0;
    while (offset < contentLength) {
      int nextLine = indexOfNewline(input, offset, contentLength);
      if (nextLine == -1) {
        if (myBufferedCr) sendBufferLine();
        myLineBuffer.append(input, offset, contentLength - offset);
        break;
      }

      boolean isCr = input[nextLine] == '\r';
      if (isCr && nextLine + 1 == contentLength) { // CR at buffer end
        if (myBufferedCr) sendBufferLine();
        myLineBuffer.append(input, offset, nextLine - offset);
        myBufferedCr = true;
        break;
      }

      if (myBufferedCr) {
        if (input[offset] == '\n') {
          myBufferedCr = false; // CRLF on buffer edge
        }
        else {
          sendBufferLine();
        }
      }

      boolean isCrLf = isCr && input[nextLine + 1] == '\n';
      boolean isCrLine = isCr && !isCrLf;

      if (myLineBuffer.length() == 0) {
        String text = new String(input, offset, nextLine - offset);
        myLineConsumer.consume(text, isCrLine);
      }
      else {
        myBufferedCr = isCrLine;
        myLineBuffer.append(input, offset, nextLine - offset);
        sendBufferLine();
      }

      offset = isCrLf ? nextLine + 2 : nextLine + 1;
    }
  }

  private static int indexOfNewline(char[] input, int offset, int contentLength) {
    for (int i = offset; i < contentLength; i++) {
      char character = input[i];
      if (character == '\n' || character == '\r') return i;
    }
    return -1;
  }

  private void sendBufferLine() {
    myLineConsumer.consume(myLineBuffer.toString(), myBufferedCr);

    myLineBuffer.setLength(0);
    myBufferedCr = false;
  }

  /**
   * Flush incomplete lines buffer to consumer
   */
  public void flush() {
    if (myLineBuffer.length() > 0 || myBufferedCr) {
      sendBufferLine();
    }
  }

  interface LineConsumer {
    void consume(@NotNull String line, boolean isCrOnly);
  }
}