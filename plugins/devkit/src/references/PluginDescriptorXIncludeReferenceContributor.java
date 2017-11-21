// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.XmlBaseReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

/**
 * XIncludes in plugin.xml files are resolved in a bit different way than usually.
 * This class along with {@link PluginDescriptorXIncludeFileReferenceHelper} provides the way
 * to resolve 'href's in 'include' tags relatively to module resource roots.
 */
public class PluginDescriptorXIncludeReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(getPattern(), new XIncludeReferenceProvider(), PsiReferenceRegistrar.HIGHER_PRIORITY);
  }

  private static XmlAttributeValuePattern getPattern() {
    return XmlPatterns.xmlAttributeValue().withLocalName("href")
      .withSuperParent(2, XmlPatterns.xmlTag().withLocalName("include"))
      .with(new PatternCondition<XmlAttributeValue>("XInclude inside plugin.xml") {
        @Override
        public boolean accepts(@NotNull XmlAttributeValue value, ProcessingContext context) {
          return PsiUtil.isPluginXmlPsiElement(value);
        }
      });
  }

  private static class XIncludeReferenceProvider extends XmlBaseReferenceProvider {
    public XIncludeReferenceProvider() {
      super(true);
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      PsiFile file = CompletionUtil.getOriginalOrSelf(element).getContainingFile();
      return new FileReferenceSet(element) {
        @Override
        protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
          return item -> DescriptorUtil.isPluginXml(item.getContainingFile()) && !file.equals(item);
        }
      }.getAllReferences();
    }
  }
}
