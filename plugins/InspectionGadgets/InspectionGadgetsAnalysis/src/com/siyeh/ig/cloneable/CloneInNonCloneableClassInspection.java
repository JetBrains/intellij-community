/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DelegatingFixFactory;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CloneInNonCloneableClassInspection extends BaseInspection {

  private boolean onlyWarnOnPublicClone = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    if (aClass.isInterface()) {
      return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.interface.problem.descriptor", className);
    }
    else {
      return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.class.problem.descriptor", className);
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("only.warn.on.public.clone.methods"),
                                          this, "onlyWarnOnPublicClone");
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      if ("onlyWarnOnPublicClone".equals(option.getAttributeValue("name"))) {
        onlyWarnOnPublicClone = Boolean.parseBoolean(option.getAttributeValue("value"));
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (!onlyWarnOnPublicClone) {
      node.addContent(new Element("option").setAttribute("name", "onlyWarnOnPublicClone")
                        .setAttribute("value", String.valueOf(onlyWarnOnPublicClone)));
    }
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
      // only warn on public clone() option, enabled by default
      if (!CloneUtils.isClone(method)) {
        return;
      }
      if (onlyWarnOnPublicClone && !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (CloneUtils.isCloneable(containingClass) || CloneUtils.onlyThrowsException(method)) {
        return;
      }
      registerMethodError(method, containingClass);
    }
  }
}