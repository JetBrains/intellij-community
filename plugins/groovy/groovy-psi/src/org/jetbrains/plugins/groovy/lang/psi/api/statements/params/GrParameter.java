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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.params;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public interface GrParameter extends PsiParameter, GrVariable, GrCondition {
  GrParameter[] EMPTY_ARRAY = new GrParameter[0];
  ArrayFactory<GrParameter> ARRAY_FACTORY = new ArrayFactory<GrParameter>() {
    @NotNull
    @Override
    public GrParameter[] create(int count) {
      return new GrParameter[count];
    }
  };

  @Override
  @Nullable
  GrTypeElement getTypeElementGroovy();

  @Override
  @Nullable
  GrExpression getInitializerGroovy();

  @Override
  @NotNull
  GrModifierList getModifierList();

  boolean isOptional();

  /**
   * parameter can be vararg while getEllipsisDots() return null
   */
  @Nullable
  PsiElement getEllipsisDots();
}
