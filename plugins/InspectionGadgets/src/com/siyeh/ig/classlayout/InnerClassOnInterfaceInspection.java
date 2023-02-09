/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveClassFix;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class InnerClassOnInterfaceInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInnerInterfaces = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreInnerInterfaces", InspectionGadgetsBundle.message(
        "inner.class.on.interface.ignore.option")));
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MoveClassFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "InnerClassOfInterface";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass parentInterface = (PsiClass)infos[0];
    final String interfaceName = parentInterface.getName();
    return InspectionGadgetsBundle.message(
      "inner.class.on.interface.problem.descriptor", interfaceName);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassOnInterfaceVisitor();
  }

  private class InnerClassOnInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiClass[] innerClasses = aClass.getInnerClasses();
      for (final PsiClass innerClass : innerClasses) {
        if (isInnerClass(innerClass)) {
          registerClassError(innerClass, aClass);
        }
      }
    }

    private boolean isInnerClass(PsiClass innerClass) {
      if (innerClass.isEnum()) {
        return false;
      }
      if (innerClass.isAnnotationType()) {
        return false;
      }
      if (innerClass instanceof PsiTypeParameter ||
          innerClass instanceof PsiAnonymousClass) {
        return false;
      }
      return !(innerClass.isInterface() && m_ignoreInnerInterfaces);
    }
  }
}