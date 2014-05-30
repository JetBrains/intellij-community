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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
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
    return "Replace ':' with 'in'";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace for-each operator";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
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
      public boolean satisfiedBy(PsiElement element) {
        return true;
      }
    };
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
