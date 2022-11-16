// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ElementStatusTracker;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;

public class ElementStatusTrackerImpl implements ElementStatusTracker {
  private final Project myProject;

  public ElementStatusTrackerImpl(@NotNull Project project) {
    myProject = project;
  }
  
  @Override
  public @NotNull FileStatus getElementStatus(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return FileStatus.NOT_CHANGED;
    Document document = file.getViewProvider().getDocument();
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(document);
    if (tracker == null) return FileStatus.NOT_CHANGED;
    TextRange range = element.getTextRange();
    if (range == null || range.getEndOffset() > document.getTextLength()) return FileStatus.NOT_CHANGED;
    int start = document.getLineNumber(range.getStartOffset());
    int end = document.getLineNumber(range.getEndOffset());
    BitSet set = new BitSet();
    set.set(start, end + 1);
    List<? extends Range> ranges = tracker.getRangesForLines(set);
    if (ranges == null || ranges.isEmpty()) return FileStatus.NOT_CHANGED;
    if (ranges.size() == 1) {
      Range r = ranges.get(0);
      if (r.getType() == Range.INSERTED && r.getLine1() <= start && r.getLine2() >= end) {
        return FileStatus.ADDED;
      }
    }
    return FileStatus.MODIFIED;
  }
}
