package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.StringBuilderSpinAllocator;

import java.util.ArrayList;
import java.util.StringTokenizer;

final class AntMessage {
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
    ArrayList<String> lines = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(myText, "\r\n");
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    myTextLines = lines.toArray(new String[lines.size()]);
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
