// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.references;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SoftFileReferenceSet;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class FileReferenceInjector extends ReferenceInjector {
  @Override
  public @NotNull String getId() {
    return "file-reference";
  }

  @Override
  public @NotNull String getDisplayName() {
    return IntelliLangBundle.message("reference.injection.display.name.file.reference");
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Related;
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiElement element, @NotNull ProcessingContext context, @NotNull TextRange range) {
    String text = range.substring(element.getText());
    return new SoftFileReferenceSet(text, element, range.getStartOffset(), null, true).getAllReferences();
  }
}
