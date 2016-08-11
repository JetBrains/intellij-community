/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiTreeUtil;
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
      final Set<PsiClassType> thrownTypes = ExceptionUtils.calculateExceptionsThrown(tryBlock);
      ExceptionUtils.calculateExceptionsThrown(statement.getResourceList(), thrownTypes);
      final Set<PsiType> caughtTypes = new HashSet<>(thrownTypes.size());
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      boolean runtimeExceptionSeen = false;
      for (final PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        if (parameter == null) {
          continue;
        }
        final PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement == null) {
          continue;
        }
        final PsiTypeElement[] children = PsiTreeUtil.getChildrenOfType(typeElement, PsiTypeElement.class);
        if (children != null) {
          for (PsiTypeElement child : children) {
            runtimeExceptionSeen = check(thrownTypes, child, runtimeExceptionSeen, caughtTypes);
          }
        }
        else {
          runtimeExceptionSeen = check(thrownTypes, typeElement, runtimeExceptionSeen, caughtTypes);
        }
      }
    }

    private boolean check(Set<PsiClassType> thrownTypes, PsiTypeElement caughtTypeElement, boolean runtimeExceptionSeen, Set<PsiType> caughtTypes) {
      final PsiType caughtType = caughtTypeElement.getType();
      if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(caughtType.getCanonicalText())) {
        runtimeExceptionSeen = true;
      }
      else if (thrownTypes.isEmpty() && CommonClassNames.JAVA_LANG_EXCEPTION.equals(caughtType.getCanonicalText())) {
        if (!runtimeExceptionSeen) {
          final PsiClassType runtimeExceptionType = TypeUtils.getType(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, caughtTypeElement);
          registerError(caughtTypeElement, Collections.singletonList(runtimeExceptionType), caughtTypeElement);
        }
      }
      final List<PsiType> maskedExceptions = findMaskedExceptions(thrownTypes, caughtType, caughtTypes);
      if (maskedExceptions.isEmpty()) {
        return runtimeExceptionSeen;
      }
      registerError(caughtTypeElement, maskedExceptions, caughtTypeElement);
      return runtimeExceptionSeen;
    }

    private List<PsiType> findMaskedExceptions(Set<PsiClassType> thrownTypes, PsiType caughtType, Set<PsiType> caughtTypes) {
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
      final List<PsiType> maskedTypes = new ArrayList<>();
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
