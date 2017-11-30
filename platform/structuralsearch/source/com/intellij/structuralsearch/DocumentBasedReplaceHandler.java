// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.util.containers.HashMap;

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
    if (info.getMatchesCount() == 0) return;
    RangeMarker rangeMarker = myRangeMarkers.get(info);
    Document document = rangeMarker.getDocument();
    document.replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), info.getReplacement());
    PsiDocumentManager.getInstance(myProject).commitDocument(document);
  }

  @Override
  public void prepare(ReplacementInfo info) {
  MatchResult result = info.getMatchResult();
    PsiElement element = result.getMatch();
    PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    assert document !=  null;
    RangeMarker rangeMarker = document.createRangeMarker(element.getTextRange());
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    myRangeMarkers.put(info, rangeMarker);
  }
}
