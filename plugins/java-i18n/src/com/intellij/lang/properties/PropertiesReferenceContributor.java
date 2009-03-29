/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.properties;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PropertiesReferenceContributor extends PsiReferenceContributor{
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(literalExpression(), new PropertiesReferenceProvider(true));
    registrar.registerReferenceProvider(literalExpression().withParent(
      psiNameValuePair().withName(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)),
                                        new ResourceBundleReferenceProvider());

    registrar.registerReferenceProvider(PsiJavaPatterns.psiElement(PropertyValueImpl.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        String text = element.getText();
        String[] words = text.split("\\s");
        if (words.length == 0) return PsiReference.EMPTY_ARRAY;
        return new JavaClassReferenceProvider(element.getProject()){
          public boolean isSoft() {
            return true;
          }
        }.getReferencesByString(words[0], element, 0);
      }
    });
  }
}
