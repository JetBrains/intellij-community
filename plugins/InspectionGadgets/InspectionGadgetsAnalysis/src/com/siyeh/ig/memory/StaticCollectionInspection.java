/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.memory;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

public class StaticCollectionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreWeakCollections = false;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.collection.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreWeakCollections", InspectionGadgetsBundle.message(
        "static.collection.ignore.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticCollectionVisitor();
  }

  private class StaticCollectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiType type = field.getType();
      if (!CollectionUtils.isCollectionClassOrInterface(type)) {
        return;
      }
      if (m_ignoreWeakCollections && CollectionUtils.isWeakCollectionClass(type)) {
        return;
      }
      registerFieldError(field);
    }
  }
}