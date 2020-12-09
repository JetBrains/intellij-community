// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.StringTokenizer;

public final class AntMessage {
  private final AntBuildMessageView.MessageType myType;
  @Priority
  private final int myPriority;
  private final @Nls String myText;
  private final @Nls String[] myTextLines;
  private final VirtualFile myFile;
  private final int myLine;
  private final int myColumn;

  public AntMessage(AntBuildMessageView.MessageType type, @Priority int priority, @Nls String text, VirtualFile file, int line, int column) {
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
    myTextLines = ArrayUtilRt.toStringArray(lines);
  }

  public AntMessage(AntBuildMessageView.MessageType type, @Priority int priority, @Nls String[] lines, VirtualFile file, int line, int column) {
    myType = type;
    myPriority = priority;
    myFile = file;
    myLine = line;
    myColumn = column;
    myTextLines = lines;
    myText = StringUtil.join(lines, "\n");
  }

  public AntBuildMessageView.MessageType getType() {
    return myType;
  }

  @Priority
  public int getPriority() {
    return myPriority;
  }

  public @Nls String getText() {
    return myText;
  }

  public @Nls String[] getTextLines() {
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

  @NotNull
  public AntMessage withText(@NotNull @Nls String text) {
    return new AntMessage(getType(), getPriority(), text, getFile(), getLine(), getColumn());
  }

  @MagicConstant(intValues = {AntBuildMessageView.PRIORITY_ERR, AntBuildMessageView.PRIORITY_WARN, AntBuildMessageView.PRIORITY_INFO, AntBuildMessageView.PRIORITY_VERBOSE, AntBuildMessageView.PRIORITY_DEBUG})
  public @interface Priority {}
}
