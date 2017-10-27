// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collection;
import java.util.Collections;

public class PluginXmlFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return DescriptorUtil.isPluginXml(file);
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new FindUsagesHandler(element) {
      @NotNull
      @Override
      public Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target, @NotNull SearchScope searchScope) {
        if (isExtensionOrderAttributeValue(target)) {
          // Avoid highlighting 'order' keyword usages (which is done incorrectly by IdentifierHighlighterPass).
          return Collections.emptyList();
        }
        return super.findReferencesToHighlight(target, searchScope);
      }
    };
  }

  private static boolean isExtensionOrderAttributeValue(@NotNull PsiElement element) {
    if (!(element instanceof XmlAttributeValue)) {
      return false;
    }
    XmlAttributeValue attributeValue = (XmlAttributeValue)element;
    PsiElement attributeValueParent = attributeValue.getParent();
    if (!(attributeValueParent instanceof XmlAttribute)) {
      return false;
    }

    DomManager domManager = DomManager.getDomManager(element.getProject());
    GenericAttributeValue genericAttributeValue = domManager.getDomElement((XmlAttribute)attributeValueParent);
    if (genericAttributeValue == null) {
      return false;
    }

    Extension extension = genericAttributeValue.getParentOfType(Extension.class, false);
    if (extension == null) {
      return false;
    }
    return genericAttributeValue.equals(extension.getOrder());
  }
}
