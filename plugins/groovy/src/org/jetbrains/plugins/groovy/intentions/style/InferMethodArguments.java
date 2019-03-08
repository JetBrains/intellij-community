// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author knisht
 */
public class InferMethodArguments implements IntentionAction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("infer.method.arguments");
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("infer.method.arguments.for.method.declaration");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (editor == null) return false;
    int offset = editor.getCaretModel().getOffset();
    return findMethod(file, offset) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Nullable
  private static GrMethod findMethod(PsiFile file, final int offset) {
    final PsiElement at = file.findElementAt(offset);
    if (at == null) return null;

    final GrMethod method = PsiTreeUtil.getParentOfType(at, GrMethod.class, false, GrTypeDefinition.class, GrClosableBlock.class);
    if (method == null) return null;

    final TextRange methodRange = method.getTextRange();
    if (!methodRange.contains(offset) && !methodRange.contains(offset - 1)) return null;

    GrParameter[] parameters = method.getParameters();
    if (Arrays.stream(parameters).map(GrParameter::getTypeElementGroovy).anyMatch(Objects::isNull)) {
      return method;
    }
    else {
      return null;
    }
  }
}
