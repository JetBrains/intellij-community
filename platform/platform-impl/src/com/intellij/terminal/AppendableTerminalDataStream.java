/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.terminal;

import com.jediterm.terminal.TerminalDataStream;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author traff
 */
public class AppendableTerminalDataStream implements TerminalDataStream, Appendable {
  private final LinkedBlockingDeque<Character> myQueue = new LinkedBlockingDeque<>(10000000);
  private final LinkedBlockingDeque<Character> myPushBackQueue = new LinkedBlockingDeque<>();

  @Override
  public char getChar() throws IOException {
    Character ch = myPushBackQueue.poll();
    if (ch != null) {
      return ch;
    }
    try {
      return myQueue.take();
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void pushChar(char c) {
    myPushBackQueue.addFirst(c);
  }

  @Override
  public String readNonControlCharacters(int maxLength) {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < maxLength) {
      Character c = myPushBackQueue.peek();
      if (c != null) {
        if (c.charValue() < 32) {
          break;
        }
        sb.append(myPushBackQueue.poll());
      }
      else {
        c = myQueue.peek();
        if (c == null || c.charValue() < 32) {
          break;
        }
        sb.append(myQueue.poll());
      }
    }

    return sb.toString();
  }

  @Override
  public void pushBackBuffer(char[] chars, int length) {
    for (int i = length - 1; i >= 0; i--) {
      myPushBackQueue.addFirst(chars[i]);
    }
  }

  @Override
  public Appendable append(CharSequence csq) throws IOException {
    return append(csq, 0, csq.length());
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) throws IOException {
    for (int i = start; i < end; i++) {
      append(csq.charAt(i));
    }
    return this;
  }

  @Override
  public Appendable append(char c) throws IOException {
    try {
      myQueue.put(c);
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
    return this;
  }
}
