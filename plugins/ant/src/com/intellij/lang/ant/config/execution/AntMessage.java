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
package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;

import java.util.ArrayList;
import java.util.StringTokenizer;

public final class AntMessage {
  private final AntBuildMessageView.MessageType myType;
  private final int myPriority;
  private final String myText;
  private final String[] myTextLines;
  private final VirtualFile myFile;
  private final int myLine;
  private final int myColumn;

  public AntMessage(AntBuildMessageView.MessageType type, int priority, String text, VirtualFile file, int line, int column) {
    myType = type;
    myPriority = priority;
    myFile = file;
    myLine = line;
    myColumn = column;
    myText = text;
    ArrayList<String> lines = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(myText, "\r\n");
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    myTextLines = ArrayUtil.toStringArray(lines);
  }

  public AntMessage(AntBuildMessageView.MessageType type, int priority, String[] lines, VirtualFile file, int line, int column) {
    myType = type;
    myPriority = priority;
    myFile = file;
    myLine = line;
    myColumn = column;
    myTextLines = lines;
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (final String aLine : lines) {
        builder.append(aLine);
        builder.append('\n');
      }
      myText = builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntBuildMessageView.MessageType getType() {
    return myType;
  }

  public int getPriority() {
    return myPriority;
  }

  public String getText() {
    return myText;
  }

  public String[] getTextLines() {
    return myTextLines;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public int getLine() {
    return myLine;
  }

  public int getColumn() {
    return myColumn;
  }
}
