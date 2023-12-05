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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

public class SplitElseIfIntention extends GrPsiUpdateIntention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SplitElseIfPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    final GrStatement elseBranch = parentStatement.getElseBranch();

    GrIfStatement ifStatement = (GrIfStatement)parentStatement.copy();

    GrBlockStatement blockStatement = GroovyPsiElementFactory.getInstance(context.project())
      .createBlockStatementFromText("{\nabc()\n}", null);
    GrBlockStatement newBlock = ifStatement.replaceElseBranch(blockStatement);

    newBlock.getBlock().getStatements()[0].replace(elseBranch);

    parentStatement.replaceWithStatement(ifStatement);
  }
}
