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
package com.intellij.openapi.diff;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LineTokenizer extends LineTokenizerBase<String> {
  private final char[] myChars;
  private final String myText;

  public LineTokenizer(@NotNull String text) {
    myChars = text.toCharArray();
    myText = text;
  }

  @NotNull
  public String[] execute() {
    ArrayList<String> lines = new ArrayList<String>();
    doExecute(lines);
    return ArrayUtil.toStringArray(lines);
  }

  @Override
  protected void addLine(List<String> lines, int start, int end, boolean appendNewLine) {
    if (appendNewLine) {
      lines.add(myText.substring(start, end) + "\n");
    }
    else {
      lines.add(myText.substring(start, end));
    }
  }

  @Override
  protected char charAt(int index) {
    return myChars[index];
  }

  @Override
  protected int length() {
    return myChars.length;
  }

  @NotNull
  @Override
  protected String substring(int start, int end) {
    return myText.substring(start, end);
  }

  @NotNull
  public static String concatLines(@NotNull String[] lines) {
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      buffer.append(line);
    }
    return buffer.substring(0, buffer.length());
  }

  @NotNull
  public static String correctLineSeparators(@NotNull String text) {
    return concatLines(new LineTokenizer(text).execute());
  }

}
