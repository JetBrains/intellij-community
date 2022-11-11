// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class DocumentBasedReplaceHandler extends StructuralReplaceHandler {
  private final Project myProject;
  private final Map<ReplacementInfo, RangeMarker> myRangeMarkers = new HashMap<>();

  public DocumentBasedReplaceHandler(Project project) {
    myProject = project;
  }

  @Override
  public void replace(@NotNull ReplacementInfo info, @NotNull ReplaceOptions options) {
    final RangeMarker rangeMarker = myRangeMarkers.get(info);
    final Document document = rangeMarker.getDocument();
    document.replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), info.getReplacement());
    PsiDocumentManager.getInstance(myProject).commitDocument(document);
  }

  @Override
  public void prepare(@NotNull ReplacementInfo info) {
    final PsiElement firstElement = StructuralSearchUtil.getPresentableElement(info.getMatch(0));
    if (firstElement == null) return;
    final PsiFile file = firstElement.getContainingFile();
    final FileViewProvider fileViewProvider = file.getViewProvider();
    final Document document = fileViewProvider.getDocument();
    assert document !=  null;
    final TextRange range = firstElement.getTextRange();
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    final int count = info.getMatchesCount();
    for (int i = 1; i < count; i++) {
      final PsiElement match = info.getMatch(i);
      if (match == null) {
        continue;
      }
      final TextRange range1 = match.getTextRange();
      startOffset = Math.min(startOffset, range1.getStartOffset());
      endOffset = Math.max(endOffset, range1.getEndOffset());
    }
    final RangeMarker rangeMarker = document.createRangeMarker(startOffset, endOffset);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    myRangeMarkers.put(info, rangeMarker);
  }
}
