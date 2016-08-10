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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSEMI;

public class GrUnnecessarySemicolonInspection extends GroovySuppressableInspectionTool {

  private static final TokenSet SET = TokenSet.orSet(TokenSets.WHITE_SPACES_OR_COMMENTS, TokenSet.create(mSEMI));
  private static final LocalQuickFix FIX = new LocalQuickFix() {

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Remove semicolon";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
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
      public void visitElement(@NotNull PsiElement element) {
        if (element.getNode().getElementType() != mSEMI) return;
        if (!isSemicolonUnnecessary(element)) return;
        holder.registerProblem(element, "Semicolon is unnecessary", ProblemHighlightType.LIKE_UNUSED_SYMBOL, FIX);
      }
    };
  }

  private static boolean isSemicolonUnnecessary(@NotNull PsiElement semicolon) {
    PsiElement next = PsiUtil.skipSet(semicolon.getNextSibling(), true, SET, false);
    if (next == null) return true;
    if (next.getNode().getElementType() == GroovyTokenTypes.mNLS) {
      PsiElement nextSibling = next.getNextSibling();
      if (nextSibling == null || nextSibling.getNode().getElementType() != GroovyElementTypes.CLOSABLE_BLOCK) {
        return true;
      }
    }

    PsiElement previous = PsiUtil.skipWhitespacesAndComments(semicolon.getPrevSibling(), false, false);
    if (previous == null) return true;
    IElementType previousType = previous.getNode().getElementType();
    if (previousType == GroovyTokenTypes.mNLS || previousType == mSEMI) {
      return true;
    }

    return false;
  }
}
