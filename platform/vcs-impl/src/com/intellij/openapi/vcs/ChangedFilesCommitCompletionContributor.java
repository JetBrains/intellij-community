// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
public final class ChangedFilesCommitCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;

    Supplier<Iterable<Change>> changesSupplier = document.getUserData(CommitMessage.CHANGES_SUPPLIER_KEY);
    if (changesSupplier == null) return;

    result.stopHere();
    int count = parameters.getInvocationCount();

    Iterator<Change> changeIterator = changesSupplier.get().iterator();
    if (!changeIterator.hasNext()) return;

    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    if (count == 0 && prefix.length() < 5) {
      result.restartCompletionOnPrefixChange(StandardPatterns.string().withLength(5));
      return;
    }
    CompletionResultSet resultSet = result.caseInsensitive().withPrefixMatcher(
      count == 0 ? new PlainPrefixMatcher(prefix, true) : new CamelHumpMatcher(prefix));
    CompletionResultSet prefixed = result.withPrefixMatcher(new PlainPrefixMatcher(prefix, count == 0));
    while (changeIterator.hasNext()) {
      ProgressManager.checkCanceled();

      Change change = changeIterator.next();
      FilePath beforePath = ChangesUtil.getBeforePath(change);
      FilePath afterPath = ChangesUtil.getAfterPath(change);
      if (afterPath != null) {
        addFilePathName(project, resultSet, afterPath, false);
        addLanguageSpecificElements(project, count, prefixed, afterPath);
      }
      if (beforePath != null) {
        if (afterPath == null || !beforePath.getName().equals(afterPath.getName())) {
          addFilePathName(project, resultSet, beforePath, true);
        }
      }
    }
  }

  private static void addFilePathName(Project project, CompletionResultSet resultSet, FilePath filePath, boolean strikeout) {
    resultSet.addElement(LookupElementBuilder.create(filePath.getName())
                           .withIcon(VcsUtil.getIcon(project, filePath))
                           .withStrikeoutness(strikeout));
  }

  private static void addLanguageSpecificElements(Project project, int count, CompletionResultSet prefixed, FilePath filePath) {
    VirtualFile vFile = filePath.getVirtualFile();
    if (vFile == null) return;
    PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(vFile);
    if (psiFile == null) return;
    PlainTextSymbolCompletionContributor contributor = PlainTextSymbolCompletionContributorEP.forLanguage(psiFile.getLanguage());
    if (contributor == null) return;
    prefixed.addAllElements(contributor.getLookupElements(psiFile, count, prefixed.getPrefixMatcher().getPrefix()));
  }
}
