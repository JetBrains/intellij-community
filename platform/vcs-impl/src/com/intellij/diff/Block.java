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
package com.intellij.diff;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * author: lesya
 */
public class Block {
  private final String[] mySource;
  private final int myStart;
  private final int myEnd;

  public Block(@NotNull String source, int start, int end) {
    this(LineTokenizer.tokenize(source.toCharArray(), false), start, end);
  }

  public Block(@NotNull String[] source, int start, int end) {
    mySource = source;
    myStart = start;
    myEnd = end;
  }

  @NotNull
  public Block createPreviousBlock(@NotNull String prevContent) {
    return createPreviousBlock(LineTokenizer.tokenize(prevContent.toCharArray(), false));
  }

  @NotNull
  public Block createPreviousBlock(@NotNull String[] prevContent) {
    return new FindBlock(prevContent, this).getBlockInThePrevVersion();
  }

  @NotNull
  public String getBlockContent() {
    StringBuilder result = new StringBuilder();

    int length = myEnd - myStart;

    for (int i = 0; i < length; i++) {
      if ((i + myStart) >= mySource.length) break;
      result.append(mySource[i + myStart]);
      if (i < length - 1) result.append("\n");
    }

    return result.toString();
  }

  public int hashCode() {
    return Arrays.hashCode(mySource) ^ myStart ^ myEnd;
  }

  public boolean equals(Object object) {
    if (!(object instanceof Block)) return false;
    Block other = (Block)object;
    return Arrays.equals(mySource, other.mySource)
           && myStart == other.myStart
           && myEnd == other.myEnd;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  public String[] getSource() {
    return mySource;
  }

  public String toString() {
    StringBuilder result = new StringBuilder();

    appendLines(result, 0, myStart);

    result.append("<-----------------------------\n");

    appendLines(result, myStart, myEnd);

    result.append("----------------------------->\n");

    appendLines(result, myEnd, mySource.length);

    return result.toString();
  }

  private void appendLines(StringBuilder result, int from, int to) {
    for (int i = from; i < to; i++) {
      result.append(mySource[i]);
      result.append("\n");
    }
  }
}
