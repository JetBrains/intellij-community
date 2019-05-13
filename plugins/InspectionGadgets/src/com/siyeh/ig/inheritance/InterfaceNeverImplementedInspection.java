/*
 * Copyright 2006-2018 Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.PsiClass;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.InheritanceUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InterfaceNeverImplementedInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInterfacesThatOnlyDeclareConstants = false;

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignorableAnnotations");
    ignorableAnnotations.writeSettings(node, "ignorableAnnotations");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("interface.never.implemented.display.name");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return AddToIgnoreIfAnnotatedByListQuickFix.build(aClass, ignorableAnnotations);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());

    final JPanel annotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message("interface.never.implemented.option"), this,
                                           "ignoreInterfacesThatOnlyDeclareConstants");

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(annotationsPanel, constraints);

    constraints.gridy = 1;
    constraints.weighty = 0.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(checkBox, constraints);

    return panel;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "interface.never.implemented.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InterfaceNeverImplementedVisitor();
  }

  private class InterfaceNeverImplementedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (ignoreInterfacesThatOnlyDeclareConstants && aClass.getMethods().length == 0 && aClass.getFields().length != 0) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0) || InheritanceUtil.hasImplementation(aClass)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}