// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

final class JavaFxSourceReferenceProvider extends PsiReferenceProvider {
  @Override
  public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
    final FileReferenceSet set = new FileReferenceSet(((XmlAttributeValue)element).getValue(), element, 1, null, true);
    return set.getAllReferences();
  }
}
