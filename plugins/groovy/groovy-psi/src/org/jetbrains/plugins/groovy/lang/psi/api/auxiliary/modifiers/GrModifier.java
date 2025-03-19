// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiModifier;
import org.intellij.lang.annotations.MagicConstant;

/**
 * @author Maxim.Medvedev
 */
public @NlsSafe interface GrModifier extends PsiModifier {
  @NlsSafe String DEF = "def";

  @NlsSafe String VAR = "var";

  @GrModifierConstant
  String[] GROOVY_MODIFIERS =
    {PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEF, DEFAULT};

  @MagicConstant(stringValues = {DEF, PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT,
    VOLATILE, PACKAGE_LOCAL, DEFAULT})
  @interface GrModifierConstant {
  }
}
