/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.references;

import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;

/**
 * @author nik
 */
public class RuntimeModuleJavaReferenceContributor extends RuntimeModuleReferenceContributorBase {
  @Override
  @Nullable
  protected PsiExpression[] getMethodCallArguments(@NotNull PsiElement element) {
    PsiMethodCallExpression parent = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (parent == null) return null;
    return parent.getArgumentList().getExpressions();
  }

  @Override
  protected PsiElementPattern<? extends PsiLiteral, ?> literalInMethodCallParameter(PsiMethodPattern moduleMethod,
                                                                                    final int paramIndex) {
    return literalExpression().methodCallParameter(paramIndex, moduleMethod);
  }
}
