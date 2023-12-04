// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;

/**
 * @author Max Medvedev
 */
public class ReplaceDelimiterFix extends GrPsiUpdateIntention {
  @NotNull
  @Override
  public String getText(@NotNull PsiElement element) {
    return GroovyBundle.message("intention.name.replace.with.in");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("intention.family.name.replace.for.each.operator");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrForStatement forStatement = PsiTreeUtil.getParentOfType(element, GrForStatement.class);
    if (forStatement == null) return;
    GrForClause clause = forStatement.getClause();
    if (clause instanceof GrForInClause) {
      GrForStatement stubFor = (GrForStatement)GroovyPsiElementFactory.getInstance(context.project()).createStatementFromText("for (x in y){}");
      PsiElement newDelimiter = ((GrForInClause)stubFor.getClause()).getDelimiter();
      PsiElement delimiter = ((GrForInClause)clause).getDelimiter();
      delimiter.replace(newDelimiter);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return element -> true;
  }
}
