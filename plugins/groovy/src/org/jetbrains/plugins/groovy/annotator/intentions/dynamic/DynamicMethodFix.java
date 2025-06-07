// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicMethodDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isInStaticCompilationContext;

/**
 * @author Maxim.Medvedev
 */
public class DynamicMethodFix implements IntentionAction, LowPriorityAction {
  private final GrReferenceExpression myReferenceExpression;
  private final String mySignature;

  public DynamicMethodFix(GrReferenceExpression referenceExpression, final PsiType[] argumentTypes) {
    myReferenceExpression = referenceExpression;
    mySignature = calcSignature(argumentTypes);
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("add.dynamic.method.0", mySignature);
  }

  private String calcSignature(final PsiType[] argTypes) {
    StringBuilder builder = new StringBuilder().append(myReferenceExpression.getReferenceName());
    builder.append("(");

    for (int i = 0; i < argTypes.length; i++) {
      PsiType type = argTypes[i];

      if (i > 0) {
        builder.append(", ");
      }
      if (type == null) {
        builder.append("Object");
      }
      else {
        builder.append(type.getPresentableText());
      }
    }
    builder.append(")");
    return builder.toString();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, "Dynamic namespace", "", "Object " + myReferenceExpression.getReferenceName() + "()");
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myReferenceExpression.isValid() && !isInStaticCompilationContext(myReferenceExpression);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      DynamicManager.getInstance(project).addMethod(QuickfixUtil.createSettings(myReferenceExpression));
      return;
    }
    DynamicDialog dialog = new DynamicMethodDialog(myReferenceExpression);
    dialog.show();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
