/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DelegatingFixFactory;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class CloneInNonCloneableClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean onlyWarnOnPublicClone = true;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass instanceof PsiAnonymousClass anonymousClass) {
      final String text = anonymousClass.getBaseClassType().getPresentableText();
      return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.anonymous.class.problem.descriptor", text);
    }
    if (aClass.isInterface()) {
      return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.interface.problem.descriptor", aClass.getName());
    }
    else {
      return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.class.problem.descriptor", aClass.getName());
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnOnPublicClone", InspectionGadgetsBundle.message("only.warn.on.public.clone.methods")));
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    writeBooleanOption(node, "onlyWarnOnPublicClone", true);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return DelegatingFixFactory.createMakeCloneableFix((PsiClass)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneInNonCloneableClassVisitor();
  }

  private class CloneInNonCloneableClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!CloneUtils.isClone(method)) {
        return;
      }
      if (onlyWarnOnPublicClone && !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (CloneUtils.isCloneable(containingClass) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      registerMethodError(method, containingClass);
    }
  }
}