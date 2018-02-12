/*
 * Copyright 2006-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IgnoreClassFix;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AccessToStaticFieldLockedOnInstanceInspection extends AccessToStaticFieldLockedOnInstanceInspectionBase {

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(ignoredClasses, "Ignored Classes", "Choose class to ignore");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (aClass == null) {
      return null;
    }
    final String name = aClass.getQualifiedName();
    return new IgnoreClassFix(name, ignoredClasses, "Ignore static fields with type '" + name + "'");
  }
}