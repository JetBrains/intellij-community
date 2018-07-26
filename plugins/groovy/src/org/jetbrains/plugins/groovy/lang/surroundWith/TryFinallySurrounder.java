// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

public class TryFinallySurrounder extends TrySurrounder {
  @Override
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    GrTryCatchStatement tryStatement = (GrTryCatchStatement) factory.createStatementFromText("try {\n} finally{\n}", context);
    GrOpenBlock block = tryStatement.getTryBlock();
    assert block != null;
    addStatements(block, elements);
    return tryStatement;
  }

  @Override
  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / finally";
  }
}
