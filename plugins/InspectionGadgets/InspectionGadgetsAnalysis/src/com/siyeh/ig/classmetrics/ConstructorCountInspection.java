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
package com.siyeh.ig.classmetrics;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ConstructorCountInspection extends ClassMetricInspection {

  private static final int CONSTRUCTOR_COUNT_LIMIT = 5;

  public boolean ignoreDeprecatedConstructors = false;

  @Override
  @NotNull
  public String getID() {
    return "ClassWithTooManyConstructors";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("too.many.constructors.display.name");
  }

  @Override
  protected int getDefaultLimit() {
    return CONSTRUCTOR_COUNT_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("too.many.constructors.count.limit.option");
  }

  @Override
  public JComponent createOptionsPanel() {
    final JLabel label = new JLabel(getConfigurationLabel());
    final JFormattedTextField valueField = prepareNumberEditor("m_limit");
    final CheckBox includeCheckBox =
      new CheckBox(InspectionGadgetsBundle.message("too.many.constructors.ignore.deprecated.option"), this, "ignoreDeprecatedConstructors");

    final GridBag bag = new GridBag();
    bag.setDefaultInsets(0, 0, 0, UIUtil.DEFAULT_HGAP);
    bag.setDefaultAnchor(GridBagConstraints.WEST);
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(label, bag.nextLine().next());
    panel.add(valueField, bag.next().weightx(1.0));
    panel.add(includeCheckBox, bag.nextLine().next().coverLine().weighty(1.0).anchor(GridBagConstraints.NORTHWEST));
    return panel;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer count = (Integer)infos[0];
    return InspectionGadgetsBundle.message("too.many.constructors.problem.descriptor", count);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstructorCountVisitor();
  }

  private class ConstructorCountVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final int constructorCount = calculateTotalConstructorCount(aClass);
      if (constructorCount <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(constructorCount));
    }

    private int calculateTotalConstructorCount(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (!ignoreDeprecatedConstructors) {
        return constructors.length;
      }
      int count = 0;
      for (PsiMethod constructor : constructors) {
        if (!constructor.isDeprecated()) {
          count++;
        }
      }
      return count;
    }
  }
}