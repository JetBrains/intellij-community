// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template;

import com.intellij.codeInsight.template.macro.VariableTypeCalculator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;

/**
 * @author Max Medvedev
 */
public class GroovyVariableTypeCalculator extends VariableTypeCalculator {
  @Override
  public PsiType inferVarTypeAt(@NotNull PsiVariable var, @NotNull PsiElement place) {
    if (!(var instanceof GrVariable) || !(place.getLanguage() == GroovyLanguage.INSTANCE)) return null;
    if (var instanceof GrField) return var.getType();

    return TypeInferenceHelper.getVariableTypeInContext(place, (GrVariable)var);
  }
}
