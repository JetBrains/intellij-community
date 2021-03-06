// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template;

import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public abstract class GroovyTemplateContextType extends TemplateContextType {

  protected GroovyTemplateContextType(@NotNull @NonNls String id,
                                @NlsContexts.Label @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    super(id, presentableName, baseContextType);
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(GroovyLanguage.INSTANCE)) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace) {
        return false;
      }
      return element != null && isInContext(element);
    }

    return false;
  }

  protected abstract boolean isInContext(@NotNull PsiElement element);

  public static class Generic extends GroovyTemplateContextType {
    public Generic() {
      super("GROOVY", GroovyBundle.message("language.groovy"), EverywhereContextType.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return true;
    }
  }

  public static class Statement extends GroovyTemplateContextType {
    public Statement() {
      super("GROOVY_STATEMENT", GroovyBundle.message("live.template.context.statement"), Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      PsiElement stmt = PsiTreeUtil.findFirstParent(element, element11 -> PsiUtil.isExpressionStatement(element11));

      return !isAfterExpression(element) && stmt != null && stmt.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
    }

  }
  public static class Expression extends GroovyTemplateContextType {

    public Expression() {
      super("GROOVY_EXPRESSION", GroovyBundle.message("live.template.context.expression"), Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isExpressionContext(element);
    }

    private static boolean isExpressionContext(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof GrReferenceExpression)) {
        return false;
      }
      if (((GrReferenceExpression)parent).isQualified()) {
        return false;
      }
      if (parent.getParent() instanceof GrCall) {
        return false;
      }
      return !isAfterExpression(element);
    }
  }

  private static boolean isAfterExpression(PsiElement element) {
    ProcessingContext context = new ProcessingContext();
    if (PlatformPatterns.psiElement().afterLeaf(
      PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(GrExpression.class).save("prevExpr"))).accepts(element, context)) {
      PsiElement prevExpr = (PsiElement)context.get("prevExpr");
      if (prevExpr.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) {
        return true;
      }
    }
    return false;
  }

  public static class Declaration extends GroovyTemplateContextType {
    public Declaration() {
      super("GROOVY_DECLARATION", GroovyBundle.message("live.template.context.declaration"), Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (PsiTreeUtil.getParentOfType(element, GrCodeBlock.class, false, GrTypeDefinition.class) != null) {
        return false;
      }

      if (element instanceof PsiComment) {
        return false;
      }

      return GroovyCompletionData.suggestClassInterfaceEnum(element) || GroovyCompletionData.suggestFinalDef(element);
    }
  }


}
