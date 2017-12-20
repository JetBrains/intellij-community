/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicMethodDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

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
  @NotNull
  public String getText() {
    return GroovyBundle.message("add.dynamic.method") + mySignature;
  }

  private String calcSignature(final PsiType[] argTypes) {
    StringBuilder builder = new StringBuilder(" '").append(myReferenceExpression.getReferenceName());
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
    builder.append("' ");
    return builder.toString();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myReferenceExpression.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    DynamicDialog dialog = new DynamicMethodDialog(myReferenceExpression);
    dialog.show();
  }

  public void invoke(Project project) throws IncorrectOperationException {
    final DynamicElementSettings settings = QuickfixUtil.createSettings(myReferenceExpression);
    DynamicManager.getInstance(project).addMethod(settings);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
