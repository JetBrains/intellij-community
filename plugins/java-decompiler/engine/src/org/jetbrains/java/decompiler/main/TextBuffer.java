/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows to connect text with resulting lines
 *
 * @author egor
 */
public class TextBuffer {
  private final String myLineSeparator = DecompilerContext.getNewLineSeparator();
  private final String myIndent = (String)DecompilerContext.getProperty(IFernflowerPreferences.INDENT_STRING);
  private final StringBuilder myStringBuilder;
  private Map<Integer, Integer> myLineToOffsetMapping = null;

  public TextBuffer() {
    myStringBuilder = new StringBuilder();
  }

  public TextBuffer(int size) {
    myStringBuilder = new StringBuilder(size);
  }

  public void setCurrentLine(int line) {
    if (line >= 0) {
      checkMapCreated();
      myLineToOffsetMapping.put(line, myStringBuilder.length()+1);
    }
  }

  public TextBuffer append(String str) {
    myStringBuilder.append(str);
    return this;
  }

  public TextBuffer append(char ch) {
    myStringBuilder.append(ch);
    return this;
  }

  public TextBuffer appendLineSeparator() {
    myStringBuilder.append(myLineSeparator);
    return this;
  }

  public TextBuffer appendIndent(int length) {
    while (length-- > 0) {
      append(myIndent);
    }
    return this;
  }

  public TextBuffer addBanner(String banner) {
    myStringBuilder.insert(0, banner);
    if (myLineToOffsetMapping != null) {
      for (Integer line : myLineToOffsetMapping.keySet()) {
        myLineToOffsetMapping.put(line, myLineToOffsetMapping.get(line) + banner.length());
      }
    }
    return this;
  }

  @Override
  public String toString() {
    String original = myStringBuilder.toString();
    if (myLineToOffsetMapping == null || myLineToOffsetMapping.isEmpty()) {
      return original;
    }
    else {
      StringBuilder res = new StringBuilder();
      String[] srcLines = original.split(myLineSeparator);
      int currentLineStartOffset = 0;
      int currentLine = 0;
      int previousMarkLine = 0;
      int dumpedLines = 0;
      ArrayList<Integer> linesWithMarks = new ArrayList<Integer>(myLineToOffsetMapping.keySet());
      Collections.sort(linesWithMarks);
      for (Integer markLine : linesWithMarks) {
        Integer markOffset = myLineToOffsetMapping.get(markLine);
        while (currentLine < srcLines.length) {
          String line = srcLines[currentLine];
          int lineEnd = currentLineStartOffset + line.length() + myLineSeparator.length();
          if (markOffset >= currentLineStartOffset && markOffset <= lineEnd) {
            int requiredLinesNumber = markLine - dumpedLines;
            dumpedLines = markLine;
            appendLines(res, srcLines, previousMarkLine, currentLine, requiredLinesNumber);
            previousMarkLine = currentLine;
            break;
          }
          currentLineStartOffset = lineEnd;
          currentLine++;
        }
      }
      if (previousMarkLine < srcLines.length) {
        appendLines(res, srcLines, previousMarkLine, srcLines.length, srcLines.length - previousMarkLine);
      }

      return res.toString();
    }
  }

  private void appendLines(StringBuilder res, String[] srcLines, int from, int to, int requiredLineNumber) {
    if (to - from > requiredLineNumber) {
      int separatorsRequired = to - from - requiredLineNumber - 1;
      for (int i = from; i < to; i++) {
        res.append(srcLines[i]);
        if (separatorsRequired-- > 0) {
          res.append(myLineSeparator);
        }
      }
      res.append(myLineSeparator);
    }
    else if (to - from <= requiredLineNumber) {
      for (int i = from; i < to; i++) {
        res.append(srcLines[i]).append(myLineSeparator);
      }
      for (int i = 0; i < requiredLineNumber - to + from; i++) {
        res.append(myLineSeparator);
      }
    }
  }

  public int length() {
    return myStringBuilder.length();
  }

  public String substring(int start) {
    return myStringBuilder.substring(start);
  }

  public void setLength(int position) {
    myStringBuilder.setLength(position);
  }

  public TextBuffer append(TextBuffer buffer) {
    if (buffer.myLineToOffsetMapping != null && !buffer.myLineToOffsetMapping.isEmpty()) {
      checkMapCreated();
      for (Map.Entry<Integer, Integer> entry : buffer.myLineToOffsetMapping.entrySet()) {
        myLineToOffsetMapping.put(entry.getKey(), entry.getValue() + myStringBuilder.length());
      }
    }
    myStringBuilder.append(buffer.myStringBuilder);
    return this;
  }

  private void checkMapCreated() {
    if (myLineToOffsetMapping == null) {
      myLineToOffsetMapping = new HashMap<Integer, Integer>();
    }
  }

  public void insert(int offset, String s) {
    if (myLineToOffsetMapping != null) {
      throw new IllegalStateException("insert not yet supported with Line mapping");
    }
    myStringBuilder.insert(offset, s);
  }

  public int count(String substring, int from) {
    int count = 0, length = substring.length(), p = from;
    while ((p = myStringBuilder.indexOf(substring, p)) > 0) {
      ++count;
      p += length;
    }
    return count;
  }
}
