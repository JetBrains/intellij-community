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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.09.2006
 * Time: 22:17:07
 */
package com.siyeh.ig.threading;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

class NonSynchronizedMethodOverridesSynchronizedMethodVisitor extends BaseInspectionVisitor {

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    //no call to super, so we don't drill into anonymous classes
    if (method.isConstructor()) {
      return;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      return;
    }
    if (method.getNameIdentifier() == null) {
      return;
    }
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (final PsiMethod superMethod : superMethods) {
      if (superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        registerMethodError(method);
        return;
      }
    }
  }
}