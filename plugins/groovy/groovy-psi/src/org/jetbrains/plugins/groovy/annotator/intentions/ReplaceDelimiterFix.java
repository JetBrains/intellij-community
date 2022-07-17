// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;

/**
 * @author Max Medvedev
 */
public class ReplaceDelimiterFix extends Intention {
  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("intention.name.replace.with.in");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("intention.family.name.replace.for.each.operator");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    GrForStatement forStatement = PsiTreeUtil.getParentOfType(at, GrForStatement.class);
    if (forStatement == null) return;
    GrForClause clause = forStatement.getClause();
    if (clause instanceof GrForInClause) {
      GrForStatement stubFor = (GrForStatement)GroovyPsiElementFactory.getInstance(project).createStatementFromText("for (x in y){}");
      PsiElement newDelimiter = ((GrForInClause)stubFor.getClause()).getDelimiter();
      PsiElement delimiter = ((GrForInClause)clause).getDelimiter();
      delimiter.replace(newDelimiter);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return true;
      }
    };
  }
}
