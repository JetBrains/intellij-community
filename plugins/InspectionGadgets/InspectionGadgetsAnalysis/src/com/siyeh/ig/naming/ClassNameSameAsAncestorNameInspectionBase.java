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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ClassNameSameAsAncestorNameInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.name.same.as.ancestor.name.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.name.same.as.ancestor.name.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassNameSameAsAncestorNameVisitor();
  }

  private static class ClassNameSameAsAncestorNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down into inner classes
      final String className = aClass.getName();
      if (className == null) {
        return;
      }
      final Set<PsiClass> alreadyVisited = new HashSet<>(8);
      final PsiClass[] supers = aClass.getSupers();
      for (final PsiClass aSuper : supers) {
        if (hasMatchingName(aSuper, className, alreadyVisited)) {
          registerClassError(aClass);
        }
      }
    }

    private static boolean hasMatchingName(PsiClass aSuper,
                                           String className,
                                           Set<PsiClass> alreadyVisited) {
      if (aSuper == null) {
        return false;
      }
      if (alreadyVisited.contains(aSuper)) {
        return false;
      }
      alreadyVisited.add(aSuper);
      final String superName = aSuper.getName();
      if (className.equals(superName)) {
        return true;
      }
      final PsiClass[] supers = aSuper.getSupers();
      for (PsiClass aSupers : supers) {
        if (hasMatchingName(aSupers, className, alreadyVisited)) {
          return true;
        }
      }
      return false;
    }
  }
}
