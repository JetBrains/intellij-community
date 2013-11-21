/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadDeathRethrownInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ThreadDeathNotRethrown";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("thread.death.rethrown.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("thread.death.rethrown.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadDeathRethrownVisitor();
  }

  private static class ThreadDeathRethrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitCatchSection(PsiCatchSection section) {
      super.visitCatchSection(section);
      final PsiParameter parameter = section.getParameter();
      if (parameter == null) {
        return;
      }
      final PsiCodeBlock catchBlock = section.getCatchBlock();
      if (catchBlock == null) {
        return;
      }
      final PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (!hasJavaLangThreadDeathType(type) || ExceptionUtils.isThrowableRethrown(parameter, catchBlock)) {
        return;
      }
      registerVariableError(parameter);
    }

    private static boolean hasJavaLangThreadDeathType(PsiType type) {
      if (type instanceof PsiDisjunctionType) {
        final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        for (PsiType disjunction : disjunctionType.getDisjunctions()) {
          if (hasJavaLangThreadDeathType(disjunction)) {
            return true;
          }
        }
      }
      else if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (InheritanceUtil.isInheritor(aClass, "java.lang.ThreadDeath")) {
          return true;
        }
      }
      return false;
    }
  }
}
