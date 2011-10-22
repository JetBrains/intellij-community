/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ReplaceInheritanceWithDelegationFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class ExtendsConcreteCollectionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ClassExtendsConcreteCollection";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "extends.concrete.collection.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final PsiClass aClass = (PsiClass)infos[1];
    if (aClass instanceof PsiAnonymousClass) {
      return InspectionGadgetsBundle.message("anonymous.extends.concrete.collection.problem.descriptor", superClass.getQualifiedName());
    } else {
      return InspectionGadgetsBundle.message("extends.concrete.collection.problem.descriptor", superClass.getQualifiedName());
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[1];
    // skip inheritance with delegation for anonymous classes
    // or better suggest to replace anonymous with inner and then replace with delegation
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    return new ReplaceInheritanceWithDelegationFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsConcreteCollectionVisitor();
  }

  private static class ExtendsConcreteCollectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      final PsiClass superClass = aClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      if (!CollectionUtils.isCollectionClass(superClass)) {
        return;
      }
      registerClassError(aClass, superClass, aClass);
    }
  }
}