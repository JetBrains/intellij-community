/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.PsiField;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.fixes.EncapsulateVariableFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PublicFieldInspection extends PublicFieldInspectionBase {

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    panel.add(annotationsListControl, BorderLayout.CENTER);
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "public.field.ignore.enum.type.fields.option"), this, "ignoreEnums");
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList<>();
    final PsiField field = (PsiField)infos[0];
    fixes.add(new EncapsulateVariableFix(field.getName()));
    AddToIgnoreIfAnnotatedByListQuickFix.build(field, ignorableAnnotations, fixes);
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }
}