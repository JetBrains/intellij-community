// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;

/**
 * @author Maxim.Medvedev
 */
public abstract class DynamicPropertyFix extends GroovyFix implements IntentionAction, LowPriorityAction {

  @Override
  @NotNull
  public String getText() {
    return GroovyBundle.message("add.dynamic.property", getRefName());
  }

  @NotNull
  @Override
  public String getName() {
    return getText();
  }

  @Nullable
  protected abstract String getRefName();

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, "Dynamic namespace", "", "Object " + getRefName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection TestOnlyProblems
      invoke(project);
      return;
    }
    createDialog().show();
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    createDialog().show();
  }

  @NotNull
  protected abstract DynamicDialog createDialog();

  /**
   * for tests
   */
  @TestOnly
  public abstract void invoke(Project project) throws IncorrectOperationException;

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
