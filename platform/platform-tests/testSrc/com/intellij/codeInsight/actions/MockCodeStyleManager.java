// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MockCodeStyleManager extends CodeStyleManager {
  private Map<PsiFile, ChangedLines[]> myFormattedLinesForFile = new HashMap<>();

  public ChangedLines @NotNull [] getFormattedLinesFor(@NotNull PsiFile file) {
    ChangedLines[] changedLines = myFormattedLinesForFile.get(file);
    return changedLines != null ? changedLines : new ChangedLines[0];
  }

  @NotNull
  public Set<PsiFile> getFormattedFiles() {
    return myFormattedLinesForFile.keySet();
  }

  public void clearFormattedFiles() {
    myFormattedLinesForFile = new HashMap<>();
  }

  @Override
  public void reformatText(@NotNull PsiFile file, @NotNull Collection<? extends TextRange> ranges) throws IncorrectOperationException {
    ChangedLines[] formattedLines = new ChangedLines[ranges.size()];
    int i = 0;
    for (TextRange range : ranges) {
      ChangedLines lines;
      if (range.isEmpty()) {
        lines = new ChangedLines(0,0);
      }
      else {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        assert document != null : file;
        int lineStart = document.getLineNumber(range.getStartOffset());
        int lineEnd = document.getLineNumber(range.getEndOffset());
        lines = new ChangedLines(lineStart, lineEnd);
      }
      formattedLines[i++] = lines;
    }

    myFormattedLinesForFile.put(file, formattedLines);
  }

  @Override
  public void reformatTextWithContext(@NotNull PsiFile file,
                                      @NotNull ChangedRangesInfo ranges) throws IncorrectOperationException {
    //in real world ranges are optimized before passing to formatter
    reformatText(file, ranges.allChangedRanges);
  }

  @NotNull
  @Override
  public Project getProject() {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.getProject(...)");
  }

  @NotNull
  @Override
  public PsiElement reformat(@NotNull PsiElement element) throws IncorrectOperationException {
    reformatText(element.getContainingFile(), Collections.singletonList(element.getTextRange()));
    return element;
  }

  @NotNull
  @Override
  public PsiElement reformat(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    return reformat(element);
  }

  @Override
  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
    reformatText(element.getContainingFile(), startOffset, endOffset);
    return element;
  }

  @Override
  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset, boolean canChangeWhiteSpacesOnly)
    throws IncorrectOperationException {
    reformatText(element.getContainingFile(), startOffset, endOffset);
    return element;
  }

  @Override
  public void reformatText(@NotNull PsiFile file, int startOffset, int endOffset) throws IncorrectOperationException {
    reformatText(file, Collections.singletonList(new TextRange(startOffset, endOffset)));
  }

  @Override
  public void adjustLineIndent(@NotNull PsiFile file, TextRange rangeToAdjust) throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.adjustLineIndent(...)");
  }

  @Override
  public int adjustLineIndent(@NotNull PsiFile file, int offset) throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.adjustLineIndent(...)");
  }

  @Override
  public int adjustLineIndent(@NotNull Document document, int offset) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.adjustLineIndent(...)");
  }

  @Override
  public boolean isLineToBeIndented(@NotNull PsiFile file, int offset) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.isLineToBeIndented(...)");
  }

  @Nullable
  @Override
  public String getLineIndent(@NotNull PsiFile file, int offset) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.getLineIndent(...)");
  }

  @Nullable
  @Override
  public String getLineIndent(@NotNull Document document, int offset) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.getLineIndent(...)");
  }

  @Override
  public Indent getIndent(String text, FileType fileType) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.getIndent(...)");
  }

  @Override
  public String fillIndent(Indent indent, FileType fileType) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.fillIndent(...)");
  }

  @Override
  public Indent zeroIndent() {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.zeroIndent(...)");
  }

  @Override
  public void reformatNewlyAddedElement(@NotNull ASTNode block, @NotNull ASTNode addedElement)
    throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.reformatNewlyAddedElement(...)");
  }

  @Override
  public boolean isSequentialProcessingAllowed() {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.isSequentialProcessingAllowed(...)");
  }

  @Override
  public void performActionWithFormatterDisabled(Runnable r) {
    throw new UnsupportedOperationException(
      "com.intellij.codeInsight.actions.MockCodeStyleManager.performActionWithFormatterDisabled(...)");
  }

  @Override
  public <T extends Throwable> void performActionWithFormatterDisabled(ThrowableRunnable<T> r) throws T {
    throw new UnsupportedOperationException(
      "com.intellij.codeInsight.actions.MockCodeStyleManager.performActionWithFormatterDisabled(...)");
  }

  @Override
  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    throw new UnsupportedOperationException(
      "com.intellij.codeInsight.actions.MockCodeStyleManager.performActionWithFormatterDisabled(...)");
  }
}
