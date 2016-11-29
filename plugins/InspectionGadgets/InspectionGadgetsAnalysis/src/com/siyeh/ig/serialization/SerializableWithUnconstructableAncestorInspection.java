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
package com.siyeh.ig.serialization;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SerializableWithUnconstructableAncestorInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "SerializableClassWithUnconstructableAncestor";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("serializable.with.unconstructable.ancestor.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass ancestor = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("serializable.with.unconstructable.ancestor.problem.descriptor", ancestor.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableWithUnconstructableAncestorVisitor();
  }

  private static class SerializableWithUnconstructableAncestorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass) || SerializationUtils.hasWriteReplace(aClass)) {
        return;
      }
      PsiClass ancestor = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<>(8);
      while (ancestor != null && SerializationUtils.isSerializable(ancestor)) {
        if (SerializationUtils.hasWriteReplace(ancestor)) {
          return;
        }
        ancestor = ancestor.getSuperClass();
        if (!visitedClasses.add(ancestor)) {
          return;
        }
      }
      if (ancestor == null || classHasNoArgConstructor(ancestor)) {
        return;
      }
      registerClassError(aClass, ancestor);
    }

    private static boolean classHasNoArgConstructor(PsiClass aClass) {
      boolean hasConstructor = false;
      boolean hasNoArgConstructor = false;
      for (final PsiMethod constructor : aClass.getConstructors()) {
        hasConstructor = true;
        final PsiParameterList parameterList = constructor.getParameterList();
        if (parameterList.getParametersCount() == 0 &&
            (constructor.hasModifierProperty(PsiModifier.PUBLIC) || constructor.hasModifierProperty(PsiModifier.PROTECTED))) {
          hasNoArgConstructor = true;
        }
      }
      return hasNoArgConstructor || !hasConstructor;
    }
  }
}