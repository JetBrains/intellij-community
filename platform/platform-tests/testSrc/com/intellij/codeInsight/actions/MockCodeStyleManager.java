/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class MockCodeStyleManager extends CodeStyleManager {
  private Map<PsiFile, ChangedLines[]> myFormattedLinesForFile = new HashMap<>();

  @NotNull
  public ChangedLines[] getFormattedLinesFor(@NotNull PsiFile file) {
    ChangedLines[] changedLines = myFormattedLinesForFile.get(file);
    return changedLines != null ? changedLines : new ChangedLines[0];
  }

  @NotNull
  public Set<PsiFile> getFormattedFiles() {
    return myFormattedLinesForFile.keySet();
  }

  public void clearFormattedFiles() {
    myFormattedLinesForFile = ContainerUtil.newHashMap();
  }

  @Override
  public void reformatText(@NotNull PsiFile file, @NotNull Collection<TextRange> ranges) throws IncorrectOperationException {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    assert(document != null);

    ChangedLines[] formattedLines = new ChangedLines[ranges.size()];
    int i = 0;
    for (TextRange range : ranges) {
      int lineStart = document.getLineNumber(range.getStartOffset());
      int lineEnd = document.getLineNumber(range.getEndOffset());
      formattedLines[i++] = new ChangedLines(lineStart, lineEnd);
    }

    myFormattedLinesForFile.put(file, formattedLines);
  }

  @Override
  public void reformatTextWithContext(@NotNull PsiFile file, 
                                      @NotNull ChangedRangesInfo ranges) throws IncorrectOperationException {
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
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.reformat(...)");
  }

  @NotNull
  @Override
  public PsiElement reformat(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.reformat(...)");
  }

  @Override
  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.reformatRange(...)");
  }

  @Override
  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset, boolean canChangeWhiteSpacesOnly)
    throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.reformatRange(...)");
  }

  @Override
  public void reformatText(@NotNull PsiFile file, int startOffset, int endOffset) throws IncorrectOperationException {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockCodeStyleManager.reformatText(...)");
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
