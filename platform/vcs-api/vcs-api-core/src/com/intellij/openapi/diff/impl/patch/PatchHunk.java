// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.diff.impl.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PatchHunk {
  private final int myStartLineBefore;
  private final int myEndLineBefore;
  private final int myStartLineAfter;
  private final int myEndLineAfter;
  private final List<PatchLine> myLines = new ArrayList<>();

  public PatchHunk(final int startLineBefore, final int endLineBefore, final int startLineAfter, final int endLineAfter) {
    myStartLineBefore = startLineBefore;
    myEndLineBefore = endLineBefore;
    myStartLineAfter = startLineAfter;
    myEndLineAfter = endLineAfter;
  }

  public int getStartLineBefore() {
    return myStartLineBefore;
  }

  public int getEndLineBefore() {
    return myEndLineBefore;
  }

  public int getStartLineAfter() {
    return myStartLineAfter;
  }

  public int getEndLineAfter() {
    return myEndLineAfter;
  }

  public void addLine(final PatchLine line) {
    myLines.add(line);
  }

  public List<PatchLine> getLines() {
    return Collections.unmodifiableList(myLines);
  }

  public boolean isNewContent() {
    return myStartLineBefore == -1 && myEndLineBefore == -1;
  }

  public boolean isDeletedContent() {
    return myStartLineAfter == -1 && myEndLineAfter == -1;
  }

  public String getText() {
    StringBuilder builder = new StringBuilder();
    for(PatchLine line: myLines) {
      builder.append(line.getText());
      if (!line.isSuppressNewLine()) builder.append("\n");
    }
    return builder.toString();
  }

  public boolean isNoNewLineAtEnd() {
    if (myLines.isEmpty()) {
      return false;
    }
    return myLines.get(myLines.size()-1).isSuppressNewLine();
  }
}
