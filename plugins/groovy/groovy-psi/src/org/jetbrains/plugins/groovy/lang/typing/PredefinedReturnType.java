// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class PredefinedReturnType implements GrCallTypeCalculator {

  public static final Key<PsiType> PREDEFINED_RETURN_TYPE_KEY = Key.create("PREDEFINED_RETURN_TYPE_KEY");

  @Nullable
  @Override
  public PsiType getType(@Nullable PsiType receiver,
                         @NotNull PsiMethod method,
                         @Nullable List<? extends Argument> arguments,
                         @NotNull PsiElement context) {
    return method.getUserData(PREDEFINED_RETURN_TYPE_KEY);
  }
}
