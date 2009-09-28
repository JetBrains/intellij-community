/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTupleDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.02.2009
 */

public class GrMultipleVariableDeclarationImpl extends GrVariableDeclarationImpl {
  public GrMultipleVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Multiple variable definitions";
  }

  @NotNull
  public GrTupleDeclaration getTuple(){
    return findChildByClass(GrTupleDeclaration.class);
  }

  @Nullable
  public GrExpression getInitializerGroovy(){
    return findChildByClass(GrExpression.class);
  }

  public GrVariable[] getVariables() {
    return getTuple().getVariables();
  }

  public GrMember[] getMembers() {
    return GrMember.EMPTY_ARRAY;
  }
}
