/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.codeInspection;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/21/13
 */
public class MultipleRepositoryUrlsFix extends GroovyFix {
  private final GrClosableBlock myClosure;
  private final String myRepoType;

  public MultipleRepositoryUrlsFix(@NotNull GrClosableBlock closure, @NotNull String repoType) {
    myClosure = closure;
    myRepoType = repoType;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    List<GrCallExpression> statements = MultipleRepositoryUrlsInspection.findUrlCallExpressions(myClosure);
    if (statements.size() <= 1) return;
    statements.remove(0);

    List<PsiElement> elements = new ArrayList<>(statements);
    for (GrCallExpression statement : statements) {
      PsiElement newLineCandidate = statement.getNextSibling();
      if (PsiUtil.isNewLine(newLineCandidate)) {
        elements.add(newLineCandidate);
      }
    }

    myClosure.removeElements(elements.toArray(new PsiElement[elements.size()]));
    GrClosableBlock closableBlock = PsiTreeUtil.getParentOfType(myClosure, GrClosableBlock.class);
    if (closableBlock == null) return;

    GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(project);
    for (GrCallExpression statement : statements) {
      closableBlock.addStatementBefore(elementFactory.createStatementFromText(myRepoType + '{' + statement.getText() + '}'), null);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return GradleInspectionBundle.message("multiple.repository.urls.fix.name");
  }
}
