// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PointersKt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public class DynamicPropertyFromRefFix extends DynamicPropertyFix {

  private final SmartPsiElementPointer<GrReferenceExpression> myReferenceExpressionPointer;

  public DynamicPropertyFromRefFix(GrReferenceExpression referenceExpression) {
    myReferenceExpressionPointer = PointersKt.createSmartPointer(referenceExpression);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myReferenceExpressionPointer.getElement() != null;
  }

  @Nullable
  @Override
  protected String getRefName() {
    GrReferenceExpression referenceExpression = myReferenceExpressionPointer.getElement();
    return referenceExpression == null ? null : referenceExpression.getReferenceName();
  }

  @NotNull
  @Override
  protected DynamicDialog createDialog() {
    return new DynamicPropertyDialog(myReferenceExpressionPointer.getElement());
  }

  @Override
  public void invoke(Project project) throws IncorrectOperationException {
    DynamicElementSettings settings = QuickfixUtil.createSettings(myReferenceExpressionPointer.getElement());
    DynamicManager.getInstance(project).addProperty(settings);
  }

  @TestOnly
  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpressionPointer.getElement();
  }
}
