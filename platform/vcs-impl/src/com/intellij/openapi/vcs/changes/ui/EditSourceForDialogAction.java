// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.pom.Navigatable;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Internal
public class EditSourceForDialogAction extends EditSourceAction {
  private final @NotNull Component mySourceComponent;

  public EditSourceForDialogAction(@NotNull Component component) {
    super();
    Presentation presentation = getTemplatePresentation();
    presentation.setText(ActionsBundle.actionText("EditSource"));
    presentation.setIcon(AllIcons.Actions.EditSource);
    presentation.setDescription(ActionsBundle.actionDescription("EditSource"));
    mySourceComponent = component;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Navigatable[] navigatableArray = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
    if (navigatableArray != null && navigatableArray.length > 0) {
      ApplicationManager.getApplication().invokeLater(() -> OpenSourceUtil.navigate(navigatableArray));
      DialogWrapper dialog = DialogWrapper.findInstance(mySourceComponent);
      if (dialog != null && dialog.isModal()) {
        dialog.doCancelAction();
      }
    }
  }
}
