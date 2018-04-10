/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class SingletonInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("singleton.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return (Boolean)infos[0] ?
           InspectionGadgetsBundle.message("enum.singleton.problem.descriptor") :
           InspectionGadgetsBundle.message("singleton.problem.descriptor");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SingletonVisitor();
  }

  private static class SingletonVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!ClassUtils.isSingleton(aClass)) {
        return;
      }
      registerClassError(aClass, aClass.isEnum());
    }
  }
}