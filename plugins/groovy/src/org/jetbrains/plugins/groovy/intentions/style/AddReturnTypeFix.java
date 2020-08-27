// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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
public class AddReturnTypeFix implements IntentionAction {

  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("add.return.type");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("add.return.type.to.method.declaration");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (editor == null) return false;
    int offset = editor.getCaretModel().getOffset();
    return findMethod(file, offset) != null;
  }

  @Nullable
  private static GrMethod findMethod(PsiFile file, final int offset) {
    final PsiElement at = file.findElementAt(offset);
    if (at == null) return null;

    if (at.getParent() instanceof GrReturnStatement) {
      final GrReturnStatement returnStatement = ((GrReturnStatement)at.getParent());
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
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final GrMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return;
    applyFix(project, method);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void applyFix(@NotNull Project project, @NotNull GrMethod method) {
    PsiType type = method.getInferredReturnType();
    if (type == null) type = PsiType.getJavaLangObject(PsiManager.getInstance(project), method.getResolveScope());
    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(type));
    method.getModifierList().setModifierProperty(DEF, false);
  }
}
