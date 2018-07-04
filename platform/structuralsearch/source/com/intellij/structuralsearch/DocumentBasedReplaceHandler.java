// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;

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

  public void replace(ReplacementInfo info, ReplaceOptions options) {
    final RangeMarker rangeMarker = myRangeMarkers.get(info);
    final Document document = rangeMarker.getDocument();
    document.replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), info.getReplacement());
    PsiDocumentManager.getInstance(myProject).commitDocument(document);
  }

  @Override
  public void prepare(ReplacementInfo info) {
    final PsiElement firstElement = info.getMatch(0);
    if (firstElement == null) return;
    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(firstElement.getContainingFile());
    assert document !=  null;
    final TextRange range = firstElement.getTextRange();
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    int count = info.getMatchesCount();
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
