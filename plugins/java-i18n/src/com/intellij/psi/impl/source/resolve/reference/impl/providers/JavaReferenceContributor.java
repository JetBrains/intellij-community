package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

/**
 * @author peter
 */
public class JavaReferenceContributor extends PsiReferenceContributor{
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {

    final JavaClassListReferenceProvider classListProvider = new JavaClassListReferenceProvider();
    registrar.registerReferenceProvider(xmlAttributeValue(), classListProvider, PsiReferenceRegistrar.LOWER_PRIORITY);
    registrar.registerReferenceProvider(xmlTag(), classListProvider, PsiReferenceRegistrar.LOWER_PRIORITY);

    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider();
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        PsiLiteralExpression literalExpression = (PsiLiteralExpression) context;
        final Map<String, Object> annotationParams = new HashMap<String, Object>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        return !JavaI18nUtil.mustBePropertyKey(context.getProject(), literalExpression, annotationParams);
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), filePathReferenceProvider);
  }
}
