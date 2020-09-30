// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.psi.PsiModifierListOwner;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class AddToIgnoreIfAnnotatedByListQuickFix {

  private AddToIgnoreIfAnnotatedByListQuickFix() {}

  public static InspectionGadgetsFix @NotNull [] build(PsiModifierListOwner modifierListOwner, List<String> configurationList) {
    final List<InspectionGadgetsFix> fixes = build(modifierListOwner, configurationList, new ArrayList<>());
    return fixes.isEmpty() ? InspectionGadgetsFix.EMPTY_ARRAY : fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  public static List<InspectionGadgetsFix> build(final PsiModifierListOwner modifierListOwner,
                                                 final List<String> configurationList,
                                                 final List<InspectionGadgetsFix> fixes) {
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(modifierListOwner, qualifiedName -> {
      fixes.add(new DelegatingFix(SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
        InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
        QuickFixBundle.message("fix.add.special.annotation.family"),
        configurationList, qualifiedName, modifierListOwner)) {
        @NotNull
        @Override
        public Priority getPriority() {
          return Priority.LOW;
        }
      });
      return true;
    });
    return fixes;
  }
}
