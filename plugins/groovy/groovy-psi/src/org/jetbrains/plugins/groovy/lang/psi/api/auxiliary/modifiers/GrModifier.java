// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers;

import com.intellij.psi.PsiModifier;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Medvedev
 */
public interface GrModifier extends PsiModifier {
  @NonNls String DEF = "def";

  @GrModifierConstant
  String[] GROOVY_MODIFIERS =
    {PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEF, DEFAULT};

  @MagicConstant(stringValues = {DEF, PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT,
    VOLATILE, PACKAGE_LOCAL, DEFAULT})
  @interface GrModifierConstant {
  }
}
