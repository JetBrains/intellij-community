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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrLabelReference;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrFlowInterruptingStatementImpl extends GroovyPsiElementImpl implements GrFlowInterruptingStatement {
  public GrFlowInterruptingStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @Nullable
  public PsiElement getLabelIdentifier() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  @Override
  @Nullable
  public String getLabelName() {
    final PsiElement id = getLabelIdentifier();
    return id != null ? id.getText() : null;
  }

  @Override
  public PsiReference getReference() {
    final PsiElement label = getLabelIdentifier();
    if (label == null) return null;
    return new GrLabelReference(this);
  }
}
