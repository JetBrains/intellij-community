/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class AddToIgnoreIfAnnotatedByListQuickFix {

  private AddToIgnoreIfAnnotatedByListQuickFix() {}

  public static InspectionGadgetsFix[] build(PsiModifierListOwner modifierListOwner, List<String> configurationList) {
    final List<InspectionGadgetsFix> fixes = build(modifierListOwner, configurationList, new ArrayList());
    return fixes.isEmpty() ? InspectionGadgetsFix.EMPTY_ARRAY : fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  public static List<InspectionGadgetsFix> build(final PsiModifierListOwner modifierListOwner,
                                                 final List<String> configurationList,
                                                 final List<InspectionGadgetsFix> fixes) {
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(modifierListOwner, new Processor<String>() {
      @Override
      public boolean process(String qualifiedName) {
        fixes.add(new DelegatingFix(SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
          InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
          QuickFixBundle.message("fix.add.special.annotation.family"),
          configurationList, qualifiedName, modifierListOwner)));
        return true;
      }
    });
    return fixes;
  }
}
