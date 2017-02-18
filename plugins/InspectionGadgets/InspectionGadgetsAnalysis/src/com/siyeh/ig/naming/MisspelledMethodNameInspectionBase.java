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
package com.siyeh.ig.naming;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class MisspelledMethodNameInspectionBase extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean ignoreIfMethodIsOverride = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.names.differ.only.by.case.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.names.differ.only.by.case.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodNamesDifferOnlyByCaseVisitor();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private class MethodNamesDifferOnlyByCaseVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      PsiMethod[] methods = aClass.getAllMethods();
      Map<String, PsiMethod> methodNames = new THashMap<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
      Map<PsiIdentifier, String> errorNames = new THashMap<>();
      for (PsiMethod method : methods) {
        ProgressManager.checkCanceled();
        if (method.isConstructor()) continue;
        if (ignoreIfMethodIsOverride && MethodUtils.hasSuper(method)) {
          continue;
        }

        String name = method.getName();
        PsiMethod existing = methodNames.get(name);
        if (existing == null) {
          methodNames.put(name, method);
        }
        else {
          PsiClass methodClass = method.getContainingClass();
          PsiClass existingMethodClass = existing.getContainingClass();
          String existingName = existing.getName();
          if (!name.equals(existingName)) {
            if (existingMethodClass == aClass) {
              PsiIdentifier identifier = existing.getNameIdentifier();
              if (identifier != null) {
                errorNames.put(identifier, name);
              }
            }
            if (methodClass == aClass) {
              PsiIdentifier identifier = method.getNameIdentifier();
              if (identifier != null) {
                errorNames.put(identifier, existingName);
              }
            }
          }
        }
      }
      for (Map.Entry<PsiIdentifier, String> entry : errorNames.entrySet()) {
        PsiIdentifier identifier = entry.getKey();
        String otherName = entry.getValue();
        registerError(identifier, otherName);
      }
    }
  }
}
