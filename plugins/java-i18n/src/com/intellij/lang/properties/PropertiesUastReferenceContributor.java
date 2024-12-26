// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;

public final class PropertiesUastReferenceContributor extends PsiReferenceContributor {
  private static final Logger LOG = Logger.getInstance(PropertiesUastReferenceContributor.class);

  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar.registerUastReferenceProvider(registrar, injectionHostUExpression(),
                                                         new UastPropertiesReferenceProvider(true), PsiReferenceRegistrar.LOWER_PRIORITY);

    UastReferenceRegistrar.registerUastReferenceProvider(registrar,
                                                         injectionHostUExpression()
                                                           .annotationParam(AnnotationUtil.PROPERTY_KEY,
                                                                            AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER),
                                                         new ResourceBundleReferenceProvider(), PsiReferenceRegistrar.DEFAULT_PRIORITY);

    UastReferenceRegistrar
      .registerUastReferenceProvider(registrar, injectionHostUExpression(), new UastInjectionHostReferenceProvider() {
        private final ResourceBundleReferenceProvider myUnderlying = new ResourceBundleReferenceProvider();

        @Override
        public boolean acceptsTarget(@NotNull PsiElement target) {
          return target instanceof PsiFile;
        }

        @Override
        public boolean acceptsHint(@NotNull PsiReferenceService.Hints hints) {
          if (hints == PsiReferenceService.Hints.HIGHLIGHTED_REFERENCES) return false;

          return super.acceptsHint(hints);
        }

        @Override
        public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                                      @NotNull PsiLanguageInjectionHost host,
                                                                      @NotNull ProcessingContext context) {
          final UElement parent = uExpression.getUastParent();
          if (!(parent instanceof UField field)) {
            return PsiReference.EMPTY_ARRAY;
          }
          PsiElement elementSource = uExpression.getSourcePsi();
          if (elementSource == null) return PsiReference.EMPTY_ARRAY;
          UExpression initializer = field.getUastInitializer();
          if (initializer == null) return PsiReference.EMPTY_ARRAY;
          if (!field.isFinal() ||
              !field.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return PsiReference.EMPTY_ARRAY;
          }
          List<PsiReference> references = new ArrayList<>();
          final PsiClass propertyKeyAnnotation =
            JavaPsiFacade.getInstance(elementSource.getProject())
              .findClass(AnnotationUtil.PROPERTY_KEY, elementSource.getResolveScope());
          if (propertyKeyAnnotation != null) {
            LOG.assertTrue(propertyKeyAnnotation.isAnnotationType());
            AnnotatedElementsSearch.searchPsiParameters(propertyKeyAnnotation, new LocalSearchScope(elementSource.getContainingFile()))
              .forEach(parameter -> {
                UParameter uParameter = ObjectUtils.tryCast(UastContextKt.toUElement(parameter), UParameter.class);
                if (uParameter == null) return true;
                List<UAnnotation> annotations = uParameter.getUAnnotations();
                UAnnotation uAnnotation =
                  ContainerUtil.find(annotations, anno -> AnnotationUtil.PROPERTY_KEY.equals(anno.getQualifiedName()));
                if (uAnnotation == null) return true;
                UExpression attributeValue = uAnnotation.findAttributeValue(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
                if (attributeValue instanceof UResolvable &&
                    field.equals(UastContextKt.toUElement(((UResolvable)attributeValue).resolve()))) {
                  Collections.addAll(references, myUnderlying.getReferencesForInjectionHost(uExpression, host, context));
                  return false;
                }
                return true;
              });
          }
          return references.toArray(PsiReference.EMPTY_ARRAY);
        }
      }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }
}
