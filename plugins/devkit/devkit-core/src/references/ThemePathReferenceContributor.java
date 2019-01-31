// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.ide.ui.UIThemeProvider;
import com.intellij.patterns.XmlAttributeValuePattern;
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
import org.jetbrains.idea.devkit.util.PsiUtil;

public class ThemePathReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(getPattern(), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isPluginXmlPsiElement(element)) return PsiReference.EMPTY_ARRAY;

        XmlTag tag = ((XmlAttribute)element.getParent()).getParent();
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (!(domElement instanceof Extension)) return PsiReference.EMPTY_ARRAY;

        ExtensionPoint extensionPoint = ((Extension)domElement).getExtensionPoint();
        if (extensionPoint == null) return PsiReference.EMPTY_ARRAY;
        if (!UIThemeProvider.EP_NAME.getName().equals(extensionPoint.getEffectiveQualifiedName())) return PsiReference.EMPTY_ARRAY;

        return new FileReferenceSet(element).getAllReferences();
      }
    });
  }


  private static XmlAttributeValuePattern getPattern() {
    return XmlPatterns.xmlAttributeValue().withLocalName("path");
  }
}
