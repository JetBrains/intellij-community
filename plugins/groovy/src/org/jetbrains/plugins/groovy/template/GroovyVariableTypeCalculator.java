/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    return TypeInferenceHelper.getInferredType(place, var.getName());
  }
}
