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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiElement;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class TooBroadThrowsInspection extends TooBroadThrowsInspectionBase {

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[2];
    final SuppressForTestsScopeFix suppressFix = SuppressForTestsScopeFix.build(this, context);
    if (suppressFix == null) {
      return new InspectionGadgetsFix[] {buildFix(infos)};
    }
    return new InspectionGadgetsFix[] {buildFix(infos), suppressFix};
  }
}
