// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ChangeListCompletionContributor extends CompletionContributor implements DumbAware {
  public static final Key<ComboBox<ChangeList>> COMBO_BOX_KEY = Key.create("CHANGELIST_COMBO_BOX");

  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    final PsiFile file = parameters.getOriginalFile();
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getCachedDocument(file);
    if (document == null) return;
    ComboBox comboBox = document.getUserData(COMBO_BOX_KEY);
    if (comboBox == null) return;
    final CompletionResultSet resultSet = result.withPrefixMatcher(new PlainPrefixMatcher(document.getText()));
    for (int i = 0; i < comboBox.getItemCount(); i++) {
      resultSet.addElement(LookupElementBuilder.create(comboBox.getItemAt(i)));
    }
  }

  @Override
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    PsiFile file = position.getContainingFile();
    Document cachedDocument = PsiDocumentManager.getInstance(file.getProject()).getCachedDocument(file);
    return cachedDocument != null && cachedDocument.getUserData(COMBO_BOX_KEY) != null;
  }
}

