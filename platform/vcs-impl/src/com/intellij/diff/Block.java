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

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */
public class Block {
  private final String[] mySource;
  private int myStart;
  private int myEnd;

  public Block(String source, int start, int end) {
    this(LineTokenizer.tokenize(source.toCharArray(), false),
        start, end);
  }

  public Block(String[] source, int start, int end) {
    mySource = source;
    myStart = start;
    myEnd = end;


  }

  public String getBlockContent(){
    StringBuffer result = new StringBuffer();

    int length = myEnd - myStart + 1;

    for (int i = 0; i < length; i++) {
      if ((i + myStart)>= mySource.length) break;
      result.append(mySource[i + myStart]);
      if (i < length - 1) result.append("\n");
    };

    return result.toString();
  }

  public int hashCode() {
    return getSourceAsList().hashCode() ^ myStart ^ myEnd;
  }

  private List<String> getSourceAsList() {
    return Arrays.asList(mySource);
  }

  public boolean equals(Object object) {
    if (!(object instanceof Block)) return false;
    Block other = (Block)object;
    return getSourceAsList().equals(other.getSourceAsList())
        && myStart == other.myStart
        && myEnd == other.myEnd;
  }

  public int getStart() { return myStart; }

  public int getEnd() { return myEnd; }

  public void setStart(int start) { myStart = start; }

  public void setEnd(int end) { myEnd = end; }

  public String[] getSource() {
    return mySource;
  }

  public String toString() {
    StringBuffer result = new StringBuffer();

    appendLines(result, 0, myStart);

    appendLineTo(result, "<-----------------------------");

    appendLines(result, myStart, myEnd + 1);

    appendLineTo(result, "----------------------------->");

    appendLines(result, myEnd + 1, mySource.length);

    return result.toString();
  }

  private void appendLines(StringBuffer result, int from, int to) {
    for (int i = from; i < to; i++){
      appendLineTo(result, mySource[i]);
    }
  }

  private void appendLineTo(StringBuffer result, String line) {
    result.append(line);
    result.append("\n");
  }
}
