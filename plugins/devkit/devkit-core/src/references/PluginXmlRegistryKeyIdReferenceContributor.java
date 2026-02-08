// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.PsiUtil;

import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

final class PluginXmlRegistryKeyIdReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      xmlAttributeValue()
        .withLocalName("key")
        .withSuperParent(2, xmlTag().withLocalName("registryKey")),
      new PsiReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          if (!PsiUtil.isPluginXmlPsiElement(element)) return PsiReference.EMPTY_ARRAY;

          return new PsiReference[]{new RegistryKeyIdReferenceContributor.RegistryKeyIdReference(element)};
        }
      });
  }
}
