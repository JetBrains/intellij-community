/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TooBroadCatchInspectionBase extends BaseInspection {
  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnRootExceptions = false;
  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility
  @SuppressWarnings("PublicField")
  public boolean ignoreThrown = false;

  @Override
  @NotNull
  public String getID() {
    return "OverlyBroadCatchBlock";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("too.broad.catch.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final List<PsiType> typesMasked = (List<PsiType>)infos[0];
    String typesMaskedString = typesMasked.get(0).getPresentableText();
    if (typesMasked.size() == 1) {
      return InspectionGadgetsBundle.message("too.broad.catch.problem.descriptor", typesMaskedString);
    }
    else {
      //Collections.sort(typesMasked);
      final int lastTypeIndex = typesMasked.size() - 1;
      for (int i = 1; i < lastTypeIndex; i++) {
        typesMaskedString += ", ";
        typesMaskedString += typesMasked.get(i).getPresentableText();
      }
      final String lastTypeString = typesMasked.get(lastTypeIndex).getPresentableText();
      return InspectionGadgetsBundle.message("too.broad.catch.problem.descriptor1", typesMaskedString, lastTypeString);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TooBroadCatchVisitor();
  }

  private class TooBroadCatchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCodeBlock tryBlock = statement.getTryBlock();
      if (tryBlock == null) {
        return;
      }
      final Set<PsiType> thrownTypes = ExceptionUtils.calculateExceptionsThrown(tryBlock);
      final Set<PsiType> caughtTypes = new HashSet<PsiType>(thrownTypes.size());
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (final PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        if (parameter == null) {
          continue;
        }
        final PsiType caughtType = parameter.getType();
        if (caughtType instanceof PsiDisjunctionType) {
          final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)caughtType;
          final List<PsiType> types = disjunctionType.getDisjunctions();
          for (PsiType type : types) {
            check(thrownTypes, caughtTypes, parameter, type, statement.getProject());
          }
        }
        else {
          if (thrownTypes.isEmpty()) {
            if (CommonClassNames.JAVA_LANG_EXCEPTION.equals(caughtType.getCanonicalText())) {
              final PsiTypeElement typeElement = parameter.getTypeElement();
              if (typeElement == null) {
                continue;
              }
              final PsiClassType runtimeExceptionType = TypeUtils.getType(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, parameter);
              registerError(typeElement, Collections.singletonList(runtimeExceptionType), typeElement, statement.getProject());
            }
          }
          else {
            check(thrownTypes, caughtTypes, parameter, caughtType, statement.getProject());
          }
        }
      }
    }

    private void check(Set<PsiType> thrownTypes, Set<PsiType> caughtTypes, PsiParameter parameter, PsiType caughtType, Project project) {
      final List<PsiType> maskedExceptions = findMaskedExceptions(thrownTypes, caughtTypes, caughtType);
      if (maskedExceptions.isEmpty()) {
        return;
      }
      final PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) {
        return;
      }
      registerError(typeElement, maskedExceptions, typeElement, project);
    }

    private List<PsiType> findMaskedExceptions(Set<PsiType> thrownTypes, Set<PsiType> caughtTypes, PsiType caughtType) {
      if (thrownTypes.contains(caughtType)) {
        if (ignoreThrown) {
          return Collections.emptyList();
        }
        caughtTypes.add(caughtType);
        thrownTypes.remove(caughtType);
      }
      if (onlyWarnOnRootExceptions) {
        if (!ExceptionUtils.isGenericExceptionClass(caughtType)) {
          return Collections.emptyList();
        }
      }
      final List<PsiType> maskedTypes = new ArrayList<PsiType>();
      for (PsiType typeThrown : thrownTypes) {
        if (!caughtTypes.contains(typeThrown) && caughtType.isAssignableFrom(typeThrown)) {
          caughtTypes.add(typeThrown);
          maskedTypes.add(typeThrown);
        }
      }
      return maskedTypes;
    }
  }
}
