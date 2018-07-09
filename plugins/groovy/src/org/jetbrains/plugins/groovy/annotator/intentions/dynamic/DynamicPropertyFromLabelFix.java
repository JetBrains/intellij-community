// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;

import static com.intellij.psi.util.PointersKt.createSmartPointer;

public class DynamicPropertyFromLabelFix extends DynamicPropertyFix {

  private final SmartPsiElementPointer<GrArgumentLabel> myLabelPointer;
  private final SmartPsiElementPointer<PsiClass> myTargetClassPointer;

  public DynamicPropertyFromLabelFix(@NotNull GrArgumentLabel argumentLabel, @NotNull PsiClass targetClass) {
    myLabelPointer = createSmartPointer(argumentLabel);
    myTargetClassPointer = createSmartPointer(targetClass);
  }

  @Nullable
  @Override
  protected String getRefName() {
    GrArgumentLabel namedArgument = myLabelPointer.getElement();
    return namedArgument == null ? null : namedArgument.getName();
  }

  @NotNull
  @Override
  protected DynamicDialog createDialog() {
    return new DynamicPropertyDialog(myLabelPointer.getElement(),myTargetClassPointer.getElement());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myLabelPointer.getElement() != null && myTargetClassPointer.getElement() != null;
  }

  @Override
  public void invoke(Project project) throws IncorrectOperationException {
    GrArgumentLabel argumentLabel = myLabelPointer.getElement();
    if (argumentLabel == null) return;

    PsiClass targetClass = myTargetClassPointer.getElement();
    if (targetClass == null) return;

    DynamicElementSettings settings = QuickfixUtil.createSettings(argumentLabel, targetClass);
    DynamicManager.getInstance(project).addProperty(settings);
  }
}
