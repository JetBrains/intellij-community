/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.Nullable;

class ConcreteClassUtil {

  private ConcreteClassUtil() {}

  public static boolean typeIsConcreteClass(@Nullable PsiTypeElement typeElement, boolean ignoreCastToAbstractClass) {
    if (typeElement == null) {
      return false;
    }
    final PsiType type = typeElement.getType();
    return typeIsConcreteClass(type, ignoreCastToAbstractClass);
  }

  public static boolean typeIsConcreteClass(@Nullable PsiType type, boolean ignoreCastToAbstractClass) {
    if (type == null) {
      return false;
    }
    final PsiType baseType = type.getDeepComponentType();
    if (!(baseType instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)baseType;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    if (ignoreCastToAbstractClass && aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter) {
      return false;
    }
    return !LibraryUtil.classIsInLibrary(aClass);
  }
}
