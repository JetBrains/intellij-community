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
  public TextRange getRangeInElement() {
    final PsiElement identifier = myStatement.getLabelIdentifier();
    if (identifier == null) {
      return new TextRange(-1, -2);
    }
    final int offsetInParent = identifier.getStartOffsetInParent();
    return new TextRange(offsetInParent, offsetInParent + identifier.getTextLength());
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return resolve() == element;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    final String name = myStatement.getLabelName();
    if (name == null) return "";
    return name;
  }

  @Override
  public GrFlowInterruptingStatement handleElementRename(String newElementName) throws IncorrectOperationException {
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
  @NotNull
  public Object[] getVariants() {
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
  public GrFlowInterruptingStatement getElement() {
    return myStatement;
  }

  @Override
  public PsiElement resolve() {
    return myStatement.resolveLabel();
  }
}
