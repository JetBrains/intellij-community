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
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiType;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public interface GrVariableDeclaration extends GrStatement, GrMembersDeclaration {
  ArrayFactory<GrVariableDeclaration> ARRAY_FACTORY = new ArrayFactory<GrVariableDeclaration>() {
    @NotNull
    @Override
    public GrVariableDeclaration[] create(int count) {
      return new GrVariableDeclaration[count];
    }
  };

  @Nullable
  GrTypeElement getTypeElementGroovy();

  @NotNull
  GrVariable[] getVariables();

  void setType(@Nullable PsiType type);

  boolean isTuple();

  @Nullable
  GrTypeElement getTypeElementGroovyForVariable(GrVariable var);

  @Nullable
  GrExpression getTupleInitializer();

  @Override
  boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @NotNull String name);
}
