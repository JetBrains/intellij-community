/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public abstract class GrMethodComparator {
  private static final ExtensionPointName<GrMethodComparator> EP_NAME = ExtensionPointName.create("org.intellij.groovy.methodComparator");

  public interface Context {
    @Nullable
    PsiType[] getArgumentTypes();

    @Nullable
    PsiType[] getTypeArguments();

    @Nullable
    PsiType getThisType();

    @NotNull
    PsiElement getPlace();
  }

  public abstract Boolean dominated(@NotNull PsiMethod method1,
                                    @NotNull PsiSubstitutor substitutor1,
                                    @NotNull PsiMethod method2,
                                    @NotNull PsiSubstitutor substitutor2,
                                    @NotNull Context context);

  @Nullable
  public static Boolean checkDominated(@NotNull PsiMethod method1,
                                       @NotNull PsiSubstitutor substitutor1,
                                       @NotNull PsiMethod method2,
                                       @NotNull PsiSubstitutor substitutor2,
                                       @NotNull Context context) {
    for (GrMethodComparator comparator : EP_NAME.getExtensions()) {
      Boolean result = comparator.dominated(method1, substitutor1, method2, substitutor2, context);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
