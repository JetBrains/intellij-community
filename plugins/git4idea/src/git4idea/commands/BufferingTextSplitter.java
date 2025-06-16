// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class BufferingTextSplitter {
  private final @NotNull StringBuilder myLineBuffer = new StringBuilder();
  private boolean myBufferedCr = false;

  private final @NotNull LineConsumer myLineConsumer;

  @VisibleForTesting
  public BufferingTextSplitter(@NotNull LineConsumer lineConsumer) {
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

      if (myLineBuffer.isEmpty()) {
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
    if (!myLineBuffer.isEmpty() || myBufferedCr) {
      sendBufferLine();
    }
  }

  @ApiStatus.Internal
  public interface LineConsumer {
    void consume(@NotNull String line, boolean isCrOnly);
  }
}