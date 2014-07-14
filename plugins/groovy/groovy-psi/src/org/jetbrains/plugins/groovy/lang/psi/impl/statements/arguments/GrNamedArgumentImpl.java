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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrNamedArgumentImpl extends GroovyPsiElementImpl implements GrNamedArgument {

  public GrNamedArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitNamedArgument(this);
  }

  public String toString() {
    return "Named argument";
  }

  @Override
  @Nullable
  public GrArgumentLabel getLabel() {
    return (GrArgumentLabel)findChildByType(GroovyElementTypes.ARGUMENT_LABEL);
  }


  @Override
  @Nullable
  public GrExpression getExpression() {
    return findExpressionChild(this);
  }

  @Override
  public String getLabelName() {
    final GrArgumentLabel label = getLabel();
    return label == null ? null : label.getName();
  }

  @Nullable
  @Override
  public PsiElement getColon() {
    return findChildByType(GroovyTokenTypes.mCOLON);
  }
}
