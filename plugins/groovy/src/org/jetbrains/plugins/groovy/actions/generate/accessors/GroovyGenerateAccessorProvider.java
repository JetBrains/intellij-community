// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions.generate.accessors;

import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class GroovyGenerateAccessorProvider implements NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> {

  @Override
  public @NotNull Collection<EncapsulatableClassMember> fun(PsiClass s) {
    if (!(s instanceof GrTypeDefinition)) return Collections.emptyList();
    final List<EncapsulatableClassMember> result = new ArrayList<>();
    for (PsiField field : s.getFields()) {
      if (!(field instanceof PsiEnumConstant) && field instanceof GrField) {
        result.add(new GrFieldMember(field));
      }
    }
    return result;
  }
}
