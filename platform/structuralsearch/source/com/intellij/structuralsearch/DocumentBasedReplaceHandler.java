package com.intellij.structuralsearch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementInfoImpl;
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
    assert info instanceof ReplacementInfoImpl;
    PsiElement element = info.getMatch(0);
    if (element == null) return;
    PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
    assert file != null;
    RangeMarker rangeMarker = myRangeMarkers.get(info);
    Document document = rangeMarker.getDocument();
    document.replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), info.getReplacement());
    PsiDocumentManager.getInstance(element.getProject()).commitDocument(document);
  }

  @Override
  public void prepare(ReplacementInfo info) {
    assert info instanceof ReplacementInfoImpl;
    MatchResult result = ((ReplacementInfoImpl)info).getMatchResult();
    PsiElement element = result.getMatch();
    PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    TextRange textRange = result.getMatch().getTextRange();
    assert textRange != null;
    RangeMarker rangeMarker = document.createRangeMarker(textRange);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    myRangeMarkers.put(info, rangeMarker);
  }
}
