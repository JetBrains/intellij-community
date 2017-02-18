/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.*;

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

  public TextBuffer(String text) {
    myStringBuilder = new StringBuilder(text);
  }

  public TextBuffer append(String str) {
    myStringBuilder.append(str);
    return this;
  }

  public TextBuffer append(char ch) {
    myStringBuilder.append(ch);
    return this;
  }

  public TextBuffer append(int i) {
    myStringBuilder.append(i);
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

  public TextBuffer prepend(String s) {
    insert(0, s);
    return this;
  }

  public TextBuffer enclose(String left, String right) {
    prepend(left);
    append(right);
    return this;
  }

  public boolean containsOnlyWhitespaces() {
    for (int i = 0; i < myStringBuilder.length(); i++) {
      if (myStringBuilder.charAt(i) != ' ') {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    String original = myStringBuilder.toString();
    if (myLineToOffsetMapping == null || myLineToOffsetMapping.isEmpty()) {
      if (myLineMapping != null) {
        return addOriginalLineNumbers();
      }
      return original;
    }
    else {
      StringBuilder res = new StringBuilder();
      String[] srcLines = original.split(myLineSeparator);
      int currentLineStartOffset = 0;
      int currentLine = 0;
      int previousMarkLine = 0;
      int dumpedLines = 0;
      ArrayList<Integer> linesWithMarks = new ArrayList<>(myLineToOffsetMapping.keySet());
      Collections.sort(linesWithMarks);
      for (Integer markLine : linesWithMarks) {
        Integer markOffset = myLineToOffsetMapping.get(markLine);
        while (currentLine < srcLines.length) {
          String line = srcLines[currentLine];
          int lineEnd = currentLineStartOffset + line.length() + myLineSeparator.length();
          if (markOffset <= lineEnd) {
            int requiredLine = markLine - 1;
            int linesToAdd = requiredLine - dumpedLines;
            dumpedLines = requiredLine;
            appendLines(res, srcLines, previousMarkLine, currentLine, linesToAdd);
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

  private String addOriginalLineNumbers() {
    StringBuilder sb = new StringBuilder();
    int lineStart = 0, lineEnd;
    int count = 0, length = myLineSeparator.length();
    while ((lineEnd = myStringBuilder.indexOf(myLineSeparator, lineStart)) > 0) {
      ++count;
      sb.append(myStringBuilder.substring(lineStart, lineEnd));
      Set<Integer> integers = myLineMapping.get(count);
      if (integers != null) {
        sb.append("//");
        for (Integer integer : integers) {
          sb.append(' ').append(integer);
        }
      }
      sb.append(myLineSeparator);
      lineStart = lineEnd + length;
    }
    if (lineStart < myStringBuilder.length()) {
      sb.append(myStringBuilder.substring(lineStart));
    }
    return sb.toString();
  }

  private void appendLines(StringBuilder res, String[] srcLines, int from, int to, int requiredLineNumber) {
    if (to - from > requiredLineNumber) {
      List<String> strings = compactLines(Arrays.asList(srcLines).subList(from, to) ,requiredLineNumber);
      int separatorsRequired = requiredLineNumber - 1;
      for (String s : strings) {
        res.append(s);
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

  public TextBuffer setStart(int position) {
    myStringBuilder.delete(0, position);
    shiftMapping(0, -position);
    return this;
  }

  public void setLength(int position) {
    myStringBuilder.setLength(position);
    if (myLineToOffsetMapping != null) {
      HashMap<Integer, Integer> newMap = new HashMap<>();
      for (Map.Entry<Integer, Integer> entry : myLineToOffsetMapping.entrySet()) {
        if (entry.getValue() <= position) {
          newMap.put(entry.getKey(), entry.getValue());
        }
      }
      myLineToOffsetMapping = newMap;
    }
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

  private void shiftMapping(int startOffset, int shiftOffset) {
    if (myLineToOffsetMapping != null) {
      HashMap<Integer, Integer> newMap = new HashMap<>();
      for (Map.Entry<Integer, Integer> entry : myLineToOffsetMapping.entrySet()) {
        int newValue = entry.getValue();
        if (newValue >= startOffset) {
          newValue += shiftOffset;
        }
        if (newValue >= 0) {
          newMap.put(entry.getKey(), newValue);
        }
      }
      myLineToOffsetMapping = newMap;
    }
  }

  private void checkMapCreated() {
    if (myLineToOffsetMapping == null) {
      myLineToOffsetMapping = new HashMap<>();
    }
  }

  public TextBuffer insert(int offset, String s) {
    myStringBuilder.insert(offset, s);
    shiftMapping(offset, s.length());
    return this;
  }

  public int countLines() {
    return countLines(0);
  }

  public int countLines(int from) {
    return count(myLineSeparator, from);
  }

  public int count(String substring, int from) {
    int count = 0, length = substring.length(), p = from;
    while ((p = myStringBuilder.indexOf(substring, p)) > 0) {
      ++count;
      p += length;
    }
    return count;
  }

  private static List<String> compactLines(List<String> srcLines, int requiredLineNumber) {
    if (srcLines.size() < 2 || srcLines.size() <= requiredLineNumber) {
      return srcLines;
    }
    List<String> res = new LinkedList<>(srcLines);
    // first join lines with a single { or }
    for (int i = res.size()-1; i > 0 ; i--) {
      String s = res.get(i);
      if (s.trim().equals("{") || s.trim().equals("}")) {
        res.set(i-1, res.get(i-1).concat(s));
        res.remove(i);
      }
      if (res.size() <= requiredLineNumber) {
        return res;
      }
    }
    // now join empty lines
    for (int i = res.size()-1; i > 0 ; i--) {
      String s = res.get(i);
      if (s.trim().isEmpty()) {
        res.set(i-1, res.get(i-1).concat(s));
        res.remove(i);
      }
      if (res.size() <= requiredLineNumber) {
        return res;
      }
    }
    return res;
  }

  private Map<Integer, Set<Integer>> myLineMapping = null; // new to original

  public void dumpOriginalLineNumbers(int[] lineMapping) {
    if (lineMapping.length > 0) {
      myLineMapping = new HashMap<>();
      for (int i = 0; i < lineMapping.length; i += 2) {
        int key = lineMapping[i + 1];
        Set<Integer> existing = myLineMapping.get(key);
        if (existing == null) {
          existing = new TreeSet<>();
          myLineMapping.put(key, existing);
        }
        existing.add(lineMapping[i]);
      }
    }
  }
}
