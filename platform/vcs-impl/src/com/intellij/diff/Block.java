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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.Diff;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */
public class Block {
  private static final Logger LOG = Logger.getInstance(Block.class);

  @NotNull private final String[] mySource;
  private final int myStart;
  private final int myEnd;

  public Block(@NotNull String source, int start, int end) {
    this(LineTokenizer.tokenize(source, false, false), start, end);
  }

  public Block(@NotNull String[] source, int start, int end) {
    mySource = source;
    myStart = Math.min(Math.max(0, start), source.length);
    myEnd = Math.min(Math.max(myStart, end), source.length);
  }

  @NotNull
  public Block createPreviousBlock(@NotNull String prevContent) {
    return createPreviousBlock(LineTokenizer.tokenize(prevContent, false, false));
  }

  @NotNull
  public Block createPreviousBlock(@NotNull String[] prevContent) {
    int start = -1;
    int end = -1;
    int shift = 0;

    Diff.Change change = Diff.buildChangesSomehow(prevContent, getSource());
    while (change != null) {
      int startLine1 = change.line0;
      int startLine2 = change.line1;
      int endLine1 = startLine1 + change.deleted;
      int endLine2 = startLine2 + change.inserted;

      if (Math.max(myStart, startLine2) < Math.min(myEnd, endLine2)) {
        // ranges intersect
        if (startLine2 <= myStart) start = startLine1;
        if (endLine2 > myEnd) end = endLine1;
      }
      if (startLine2 > myStart) {
        if (start == -1) start = myStart - shift;
        if (end == -1 && startLine2 >= myEnd) end = myEnd - shift;
      }

      shift += change.inserted - change.deleted;
      change = change.link;
    }
    if (start == -1) start = myStart - shift;
    if (end == -1) end = myEnd - shift;

    if (start < 0 || end > prevContent.length || end < start) {
      LOG.error("Invalid block range: [" + start + ", " + end + "); length - " + prevContent.length);
    }

    return new Block(prevContent, start, end);
  }

  @NotNull
  public String getBlockContent() {
    return StringUtil.join(getLines(), "\n");
  }

  @NotNull
  public List<String> getLines() {
    return Arrays.asList(mySource).subList(myStart, myEnd);
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

  @NotNull
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

  private void appendLines(@NotNull StringBuilder result, int from, int to) {
    for (int i = from; i < to; i++) {
      result.append(mySource[i]);
      result.append("\n");
    }
  }
}
