/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.gant.GantUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ilyas
 */
public class GantReferenceExpressionProvider extends PsiReferenceProvider {

  public static final Class SCOPE_CLASS = GrReferenceExpression.class;

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof GrReferenceExpression) {
      PsiFile file = element.getContainingFile();
      if (GantUtils.isGantScriptFile(file)) {
        return new PsiReference[]{new GantTargetReference(((GrReferenceExpression)element))};
      }

    }
    return PsiReference.EMPTY_ARRAY;
  }
}
