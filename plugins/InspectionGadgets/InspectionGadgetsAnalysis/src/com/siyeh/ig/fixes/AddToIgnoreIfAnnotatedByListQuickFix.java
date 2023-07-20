// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.psi.PsiModifierListOwner;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.DelegatingFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class AddToIgnoreIfAnnotatedByListQuickFix {

  private AddToIgnoreIfAnnotatedByListQuickFix() {}

  public static LocalQuickFix @NotNull [] build(PsiModifierListOwner modifierListOwner, List<String> configurationList) {
    final List<LocalQuickFix> fixes = build(modifierListOwner, configurationList, new ArrayList<>());
    return fixes.isEmpty() ? LocalQuickFix.EMPTY_ARRAY : fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  public static List<LocalQuickFix> build(final PsiModifierListOwner modifierListOwner,
                                          final List<String> configurationList,
                                          final List<LocalQuickFix> fixes) {
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(modifierListOwner, qualifiedName -> {
      fixes.add(new DelegatingFix(SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
        InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
        QuickFixBundle.message("fix.add.special.annotation.family"),
        InspectionGadgetsBundle.message("ignore.if.annotated.by"), configurationList, qualifiedName
      )) {
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
