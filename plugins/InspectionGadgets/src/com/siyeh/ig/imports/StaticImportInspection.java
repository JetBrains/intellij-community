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
package com.siyeh.ig.imports;

import com.intellij.psi.PsiElement;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class StaticImportInspection extends StaticImportInspectionBase {

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    final SuppressForTestsScopeFix fix = SuppressForTestsScopeFix.build(this, context);
    if (fix == null) {
      return new InspectionGadgetsFix[] {buildFix(infos)};
    }
    return new InspectionGadgetsFix[] {buildFix(infos), fix};
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    final JPanel chooserList =
      UiUtils.createTreeClassChooserList(allowedClasses, "Statically importable Classes", "Choose statically importable class");
    panel.add(chooserList, constraints);

    constraints.gridy = 1;
    constraints.weighty = 0.0;
    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.single.field.static.imports.option"), this, "ignoreSingleFieldImports");
    panel.add(checkBox1, constraints);

    constraints.gridy = 2;
    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.single.method.static.imports.option"), this, "ignoreSingeMethodImports");
    panel.add(checkBox2, constraints);

    return panel;
  }
}
