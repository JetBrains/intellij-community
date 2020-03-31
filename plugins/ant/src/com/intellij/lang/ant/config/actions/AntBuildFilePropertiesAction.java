// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.actions;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

public final class AntBuildFilePropertiesAction extends AnAction {
  private final AntExplorer myAntExplorer;

  public AntBuildFilePropertiesAction(AntExplorer antExplorer) {
    super(AntBundle.message("build.file.properties.action.name"),
          AntBundle.message("build.file.properties.action.description"),
          AllIcons.Actions.Properties);
    myAntExplorer = antExplorer;
    registerCustomShortcutSet(CommonShortcuts.ALT_ENTER, myAntExplorer);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myAntExplorer.setBuildFileProperties();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(myAntExplorer.isBuildFileSelected());
  }
}
