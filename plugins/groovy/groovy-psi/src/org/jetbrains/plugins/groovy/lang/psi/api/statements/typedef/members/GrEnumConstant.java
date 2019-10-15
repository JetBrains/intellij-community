// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public interface GrEnumConstant extends GrField, GrConstructorCall, PsiEnumConstant {
  GrEnumConstant[] EMPTY_ARRAY = new GrEnumConstant[0];
  ArrayFactory<GrEnumConstant> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrEnumConstant[count];

  @Override
  @Nullable
  GrEnumConstantInitializer getInitializingClass();

  @Nullable
  @Override
  GrArgumentList getArgumentList();

  @Nullable
  @Override
  default PsiMethod resolveMethod() {
    return GrConstructorCall.super.resolveMethod();
  }

  @Override
  @NotNull
  GroovyCallReference getConstructorReference();
}
