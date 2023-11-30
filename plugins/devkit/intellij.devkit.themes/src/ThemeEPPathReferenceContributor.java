// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.ui.UIThemeProvider;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.themes.metadata.UIThemeMetadataService;
import org.jetbrains.idea.devkit.util.PsiUtil;

final class ThemeEPPathReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withLocalName("path"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isPluginXmlPsiElement(element)) return PsiReference.EMPTY_ARRAY;

        XmlTag tag = ((XmlAttribute)element.getParent()).getParent();
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (!(domElement instanceof Extension)) return PsiReference.EMPTY_ARRAY;

        ExtensionPoint extensionPoint = ((Extension)domElement).getExtensionPoint();
        if (extensionPoint == null) return PsiReference.EMPTY_ARRAY;

        final String extensionPointQualifiedName = extensionPoint.getEffectiveQualifiedName();
        if (!UIThemeProvider.EP_NAME.getName().equals(extensionPointQualifiedName) &&
            !UIThemeMetadataService.EP_NAME.getName().equals(extensionPointQualifiedName)) {
          return PsiReference.EMPTY_ARRAY;
        }

        return new FileReferenceSet(element).getAllReferences();
      }
    });
  }
}
