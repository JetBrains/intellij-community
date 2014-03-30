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
package com.siyeh.ig.naming;

import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;

import javax.swing.*;

public class LocalVariableNamingConventionInspection extends LocalVariableNamingConventionInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  public JComponent[] createExtraOptions() {
    return new JComponent[] {
      new CheckBox(InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.option"), this, "m_ignoreForLoopParameters"),
      new CheckBox(InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.catch.option"), this, "m_ignoreCatchParameters")
    };
  }
}