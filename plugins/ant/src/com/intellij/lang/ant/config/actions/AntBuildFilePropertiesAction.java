/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AntIcons;

public final class AntBuildFilePropertiesAction extends AnAction {
  private final AntExplorer myAntExplorer;

  public AntBuildFilePropertiesAction(AntExplorer antExplorer) {
    super(AntBundle.message("build.file.properties.action.name"),
          AntBundle.message("build.file.properties.action.description"),
          AntIcons.Properties);
    myAntExplorer = antExplorer;
    registerCustomShortcutSet(CommonShortcuts.ALT_ENTER, myAntExplorer);
  }

  public void actionPerformed(AnActionEvent e) {
    myAntExplorer.setBuildFileProperties();
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(myAntExplorer.isBuildFileSelected());
  }
}
