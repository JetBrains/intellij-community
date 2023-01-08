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

import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class PropertiesClassReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider() {
      @Override
      public boolean isSoft() {
        return true;
      }
    };

    registrar.registerReferenceProvider(PsiJavaPatterns.psiElement(PropertyValueImpl.class), new PsiReferenceProvider() {
      @Override
      public boolean acceptsTarget(@NotNull PsiElement target) {
        return target instanceof PsiClass;
      }

      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        String text = element.getText();
        String[] words = text.split("\\s");
        if (words.length != 1) return PsiReference.EMPTY_ARRAY;
        return CLASS_REFERENCE_PROVIDER.getReferencesByString(words[0], element, 0);
      }
    }, PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
