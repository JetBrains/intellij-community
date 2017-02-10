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
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.psi.tree.TokenSet.andNot;
import static com.intellij.psi.tree.TokenSet.orSet;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNLS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSEMI;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.WHITE_SPACES_OR_COMMENTS;

public class GrUnnecessarySemicolonInspection extends GroovySuppressableInspectionTool implements CleanupLocalInspectionTool {

  private static final TokenSet NLS_SET = TokenSet.create(mNLS);
  private static final TokenSet FORWARD_SET = andNot(orSet(WHITE_SPACES_OR_COMMENTS, TokenSet.create(mSEMI)), NLS_SET);
  private static final TokenSet BACKWARD_SET = andNot(WHITE_SPACES_OR_COMMENTS, NLS_SET);

  private static final LocalQuickFix FIX = new LocalQuickFix() {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove semicolon";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element != null && element.getNode().getElementType() == mSEMI) {
        CodeStyleManager.getInstance(project).performActionWithFormatterDisabled((Runnable)element::delete);
      }
    }
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element.getNode().getElementType() != mSEMI) return;
        if (isSemicolonNecessary(element)) return;
        holder.registerProblem(element, "Semicolon is unnecessary", ProblemHighlightType.LIKE_UNUSED_SYMBOL, FIX);
      }
    };
  }

  private static boolean isSemicolonNecessary(@NotNull PsiElement semicolon) {
    if (semicolon.getParent() instanceof GrTraditionalForClause) return true;

    PsiElement prevSibling = PsiUtil.skipSet(semicolon, false, BACKWARD_SET);
    PsiElement nextSibling = PsiUtil.skipSet(semicolon, true, FORWARD_SET);
    return prevSibling instanceof GrStatement && (
      nextSibling instanceof GrStatement || nextSibling != null && nextSibling.getNextSibling() instanceof GrClosableBlock
    );
  }
}
