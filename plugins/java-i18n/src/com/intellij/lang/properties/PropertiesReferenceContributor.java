/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.uast.UastPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class PropertiesReferenceContributor extends PsiReferenceContributor{
  private static final Logger LOG = Logger.getInstance(PropertiesReferenceContributor.class);

  private static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider() {
    @Override
    public boolean isSoft() {
      return true;
    }
  };


  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar.registerUastReferenceProvider(registrar, UastPatterns.injectionHostUExpression(),
                                                         new UastPropertiesReferenceProvider(true), PsiReferenceRegistrar.LOWER_PRIORITY);

    UastReferenceRegistrar.registerUastReferenceProvider(registrar,
                                                         UastPatterns.injectionHostUExpression()
                                                           .annotationParam(AnnotationUtil.PROPERTY_KEY,
                                                                            AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER),
                                                         new ResourceBundleReferenceProvider(), PsiReferenceRegistrar.DEFAULT_PRIORITY);

    UastReferenceRegistrar
      .registerUastReferenceProvider(registrar, UastPatterns.injectionHostUExpression(), new UastInjectionHostReferenceProvider() {
      private final ResourceBundleReferenceProvider myUnderlying = new ResourceBundleReferenceProvider();

      @NotNull
      @Override
      public PsiReference[] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                          @NotNull PsiLanguageInjectionHost host,
                                                          @NotNull ProcessingContext context) {
        final UElement parent = uExpression.getUastParent();
        if (!(parent instanceof UField)) {
          return PsiReference.EMPTY_ARRAY;
        }
        final UField field = (UField)parent;
        UExpression initializer = field.getUastInitializer();
        if (initializer == null) return PsiReference.EMPTY_ARRAY;
        PsiElement initializerSource = initializer.getSourcePsi();
        if (initializerSource == null) return PsiReference.EMPTY_ARRAY;
        PsiElement elementSource = uExpression.getSourcePsi();
        if (initializerSource != elementSource ||
            !field.isFinal() ||
            !field.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          return PsiReference.EMPTY_ARRAY;
        }
        List<PsiReference> references = new ArrayList<>();
        final PsiClass propertyKeyAnnotation =
          JavaPsiFacade.getInstance(initializerSource.getProject()).findClass(AnnotationUtil.PROPERTY_KEY, elementSource.getResolveScope());
        if (propertyKeyAnnotation != null) {
          LOG.assertTrue(propertyKeyAnnotation.isAnnotationType());
          AnnotatedElementsSearch.searchPsiParameters(propertyKeyAnnotation, new LocalSearchScope(elementSource.getContainingFile()))
            .forEach(parameter -> {
              final PsiModifierList list = parameter.getModifierList();
              LOG.assertTrue(list != null);
              final PsiAnnotation annotation = list.findAnnotation(AnnotationUtil.PROPERTY_KEY);
              LOG.assertTrue(annotation != null);
              for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
                if (AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER.equals(pair.getName())) {
                  final PsiAnnotationMemberValue value = pair.getValue();
                  if (value instanceof PsiReferenceExpression && ((PsiReferenceExpression)value).resolve() == field.getSourcePsi()) {
                    Collections.addAll(references, myUnderlying.getReferencesForInjectionHost(uExpression, host, context));
                    return false;
                  }
                }
              }
              return true;
            });
        }
        return references.toArray(PsiReference.EMPTY_ARRAY);
      }
    }, PsiReferenceRegistrar.DEFAULT_PRIORITY);

    registrar.registerReferenceProvider(PsiJavaPatterns.psiElement(PropertyValueImpl.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        String text = element.getText();
        String[] words = text.split("\\s");
        if (words.length != 1) return PsiReference.EMPTY_ARRAY;
        return CLASS_REFERENCE_PROVIDER.getReferencesByString(words[0], element, 0);
      }
    });
  }
}
