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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import static com.intellij.psi.PsiModifier.ABSTRACT;

/**
 * Created by Max Medvedev on 16/05/14
 */
public class GrTraitUtil {
  @Contract("null -> false")
  public static boolean isInterface(@Nullable PsiClass aClass) {
    return aClass != null && aClass.isInterface() && !PsiImplUtil.isTrait(aClass);
  }

  public static boolean isMethodAbstract(PsiMethod method) {
    if (method.getModifierList().hasExplicitModifier(ABSTRACT)) return true;

    PsiClass aClass = method.getContainingClass();
    return isInterface(aClass);
  }
}
