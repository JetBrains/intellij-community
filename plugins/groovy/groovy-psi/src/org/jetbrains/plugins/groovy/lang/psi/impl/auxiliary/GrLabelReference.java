// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrLabelReference implements PsiReference {
  private GrFlowInterruptingStatement myStatement;

  public GrLabelReference(GrFlowInterruptingStatement statement) {
    myStatement = statement;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof GrLabeledStatement) {
      myStatement = handleElementRename(((GrLabeledStatement)element).getName());
    }
    throw new IncorrectOperationException("Can't bind not to labeled statement");
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final PsiElement identifier = myStatement.getLabelIdentifier();
    if (identifier == null) {
      return new TextRange(-1, -2);
    }
    final int offsetInParent = identifier.getStartOffsetInParent();
    return new TextRange(offsetInParent, offsetInParent + identifier.getTextLength());
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  @Override
  public @NotNull String getCanonicalText() {
    final String name = myStatement.getLabelName();
    if (name == null) return "";
    return name;
  }

  @Override
  public GrFlowInterruptingStatement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (myStatement instanceof GrBreakStatement) {
      myStatement = (GrFlowInterruptingStatement)myStatement.replaceWithStatement(
        GroovyPsiElementFactory.getInstance(myStatement.getProject()).createStatementFromText("break " + newElementName));
    }
    else if (myStatement instanceof GrContinueStatement) {
      myStatement = (GrFlowInterruptingStatement)myStatement.replaceWithStatement(
        GroovyPsiElementFactory.getInstance(myStatement.getProject()).createStatementFromText("continue " + newElementName));
    }
    return myStatement;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final List<PsiElement> result = new ArrayList<>();
    PsiElement context = myStatement;
    while (context != null) {
      if (context instanceof GrLabeledStatement) {
        result.add(context);
      }
      context = context.getContext();
    }
    return ArrayUtil.toObjectArray(result);
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public @NotNull GrFlowInterruptingStatement getElement() {
    return myStatement;
  }

  @Override
  public PsiElement resolve() {
    return myStatement.resolveLabel();
  }
}
