/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrParameterImpl extends GroovyPsiElementImpl implements GrParameter {
  public GrParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Parameter";
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  @NotNull
  public String getName() {
    PsiElement nameElement = getNameIdentifier();
    assert nameElement != null;
    return nameElement.getText();
  }

  @NotNull
  public PsiElement getNameIdentifier() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  public int getTextOffset() {
    return getNameIdentifier().getTextRange().getStartOffset();
  }
}
