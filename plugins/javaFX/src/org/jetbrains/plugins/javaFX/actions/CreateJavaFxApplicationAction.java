// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.ide.fileTemplates.actions.CustomCreateFromTemplateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Set;

import static org.jetbrains.plugins.javaFX.actions.JavaFxTemplateManager.isJavaFxTemplateAvailable;

final class CreateJavaFxApplicationAction extends CustomCreateFromTemplateAction implements DumbAware {
  private static final String FILE_TEMPLATE_NAME = "JavaFXApplication.java";

  CreateJavaFxApplicationAction() {
    super(FILE_TEMPLATE_NAME);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isJavaFxTemplateAvailable(e.getDataContext(), Set.of(JavaSourceRootType.SOURCE)));
  }
}
