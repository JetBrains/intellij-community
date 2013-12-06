/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.psi.PsiPolyadicExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationInspection extends StringConcatenationInspectionBase {

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final InspectionGadgetsFix[] fixes = super.buildFixes(infos);
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)infos[0];
    final SuppressForTestsScopeFix suppressFix = SuppressForTestsScopeFix.build(this, polyadicExpression);
    if (suppressFix == null) {
      return fixes;
    }
    final InspectionGadgetsFix[] newFixes = Arrays.copyOf(fixes, fixes.length + 1);
    newFixes[fixes.length] = suppressFix;
    return newFixes;
  }
}
