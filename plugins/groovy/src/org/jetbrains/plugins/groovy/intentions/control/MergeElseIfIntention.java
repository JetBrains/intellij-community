/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class MergeElseIfIntention extends GrPsiUpdateIntention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeElseIfPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    GrBlockStatement elseBlockStatement = (GrBlockStatement) parentStatement.getElseBranch();
    assert elseBlockStatement != null;
    final GrOpenBlock elseBranch = elseBlockStatement.getBlock();
    final GrStatement elseBranchContents = elseBranch.getStatements()[0];
    PsiImplUtil.replaceStatement("if(" +
                                 parentStatement.getCondition().getText() +
                                 ")" +
                                 parentStatement.getThenBranch().getText() +
                                 "else " +
                                 elseBranchContents.getText(), parentStatement);
  }
}
