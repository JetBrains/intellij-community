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
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;

/**
 * @author peter
 */
public class PropertiesReferenceContributor extends PsiReferenceContributor{

  private static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider() {
    public boolean isSoft() {
      return true;
    }
  };

  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(literalExpression(), new PropertiesReferenceProvider(true));
    registrar.registerReferenceProvider(literalExpression().withParent(
      psiNameValuePair().withName(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)),
                                        new ResourceBundleReferenceProvider());

    registrar.registerReferenceProvider(literalExpression(), new PsiReferenceProvider() {
      private final PsiReferenceProvider myUnderlying = new ResourceBundleReferenceProvider();

      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiField)) {
          return PsiReference.EMPTY_ARRAY;
        }
        final PsiField field = (PsiField)parent;
        if (field.getInitializer() != element || !field.hasModifierProperty(PsiModifier.FINAL)) {
          return PsiReference.EMPTY_ARRAY;
        }
        Set<PsiReference> references = new THashSet<>(TObjectHashingStrategy.IDENTITY);
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(element.getContainingFile(), PsiMethod.class)) {
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            final PsiModifierList modifierList = parameter.getModifierList();
            if (modifierList != null) {
              final PsiAnnotation annotation = modifierList.findAnnotation(AnnotationUtil.PROPERTY_KEY);
              if (annotation != null) {
                for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
                  if (AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER.equals(pair.getName())) {
                    final PsiAnnotationMemberValue value = pair.getValue();
                    if (value instanceof PsiReferenceExpression && ((PsiReferenceExpression)value).resolve() == field) {
                      Collections.addAll(references, myUnderlying.getReferencesByElement(element, context));
                    }
                  }
                }
              }
            }
          }
        }
        return references.toArray(new PsiReference[references.size()]);
      }
    });

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
