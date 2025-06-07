// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.DEF;

/**
 * @author Max Medvedev
 */
public final class AddReturnTypeFix extends PsiUpdateModCommandAction<PsiElement> {
  
  public AddReturnTypeFix() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("add.return.type.to.method.declaration");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return findMethod(element, context.offset()) != null ? 
           Presentation.of(GroovyBundle.message("add.return.type")) : null;
  }

  private static @Nullable GrMethod findMethod(@NotNull PsiElement at, int offset) {
    if (at.getParent() instanceof GrReturnStatement returnStatement) {
      final PsiElement word = returnStatement.getReturnWord();

      if (!word.getTextRange().contains(offset)) return null;

      final GroovyPsiElement returnOwner = PsiTreeUtil.getParentOfType(returnStatement, GrClosableBlock.class, GrMethod.class);
      if (returnOwner instanceof GrMethod) {
        final GrTypeElement returnTypeElement = ((GrMethod)returnOwner).getReturnTypeElementGroovy();
        if (returnTypeElement == null) {
          return (GrMethod)returnOwner;
        }
      }

      return null;
    }

    final GrMethod method = PsiTreeUtil.getParentOfType(at, GrMethod.class, false, GrTypeDefinition.class, GrClosableBlock.class);
    if (method == null) return null;

    final TextRange headerRange = GrHighlightUtil.getMethodHeaderTextRange(method);
    if (!headerRange.contains(offset) && !headerRange.contains(offset - 1)) return null;

    if (method.isConstructor()) return null;
    if (method.getReturnTypeElementGroovy() != null) return null;

    return method;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final GrMethod method = findMethod(element, context.offset());
    if (method == null) return;
    applyFix(context.project(), method);
  }

  public static void applyFix(@NotNull Project project, @NotNull GrMethod method) {
    PsiType type = method.getInferredReturnType();
    if (type == null) type = PsiType.getJavaLangObject(PsiManager.getInstance(project), method.getResolveScope());
    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(type));
    method.getModifierList().setModifierProperty(DEF, false);
  }
}
