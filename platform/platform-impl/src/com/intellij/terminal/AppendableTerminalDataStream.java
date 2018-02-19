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

  @Override
  public char getChar() throws IOException {
    try {
      return myQueue.take();
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void pushChar(char c) throws IOException {
    myQueue.push(c);
  }

  @Override
  public String readNonControlCharacters(int maxLength) throws IOException {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < maxLength) {
      Character c = myQueue.peek();
      if (c == null || c.charValue() < 32) {
        break;
      }
      sb.append(myQueue.poll());
    }

    return sb.toString();
  }

  @Override
  public void pushBackBuffer(char[] chars, int length) throws IOException {
    for (int i = 0; i < length; i++) {
      myQueue.addFirst(chars[length - i - i]);
    }
  }

  @Override
  public Appendable append(CharSequence csq) throws IOException {
    for (int i = 0; i<csq.length(); i++) {
      append(csq.charAt(i));
    }
    return this;
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) throws IOException {
    for (int i = start; i<end; i++) {
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
