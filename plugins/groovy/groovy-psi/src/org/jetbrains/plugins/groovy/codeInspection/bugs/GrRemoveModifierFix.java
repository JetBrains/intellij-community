// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;

import java.util.function.Function;

public class GrRemoveModifierFix extends GrModifierFix {

  public GrRemoveModifierFix(@NotNull @GrModifierConstant String modifier) {
    this(modifier, GroovyBundle.message("unnecessary.modifier.remove", modifier));
  }

  public GrRemoveModifierFix(@NotNull @GrModifierConstant String modifier, @IntentionName @NotNull String text) {
    super(text, modifier, false, MODIFIER_LIST_CHILD);
  }

  public GrRemoveModifierFix(String modifier, @NotNull Function<ProblemDescriptor, PsiModifierList> modifierListProvider) {
    super(GroovyBundle.message("unnecessary.modifier.remove", modifier), modifier, false, modifierListProvider);
  }
}
