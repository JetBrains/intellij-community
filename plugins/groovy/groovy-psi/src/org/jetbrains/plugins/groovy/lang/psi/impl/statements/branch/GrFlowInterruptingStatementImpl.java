// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
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
  public @Nullable PsiElement getLabelIdentifier() {
    PsiElement result = findChildByType(TokenSets.PROPERTY_NAMES);
    return result == null ? findChildByType(TokenSets.GROOVY_KEYWORDS) : result;
  }

  @Override
  public @Nullable String getLabelName() {
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
