// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.util.IntentionName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;

public class GrRemoveModifierFix extends GrModifierFix {

  public GrRemoveModifierFix(@NotNull @GrModifierConstant String modifier) {
    this(modifier, GroovyBundle.message("unnecessary.modifier.remove", modifier));
  }

  public GrRemoveModifierFix(@NotNull @GrModifierConstant String modifier, @IntentionName @NotNull String text) {
    super(text, modifier, false, GrModifierFix.MODIFIER_LIST_CHILD);
  }
}
