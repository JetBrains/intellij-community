/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.griffon;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.MvcActionBase;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;

/**
 * @author peter
 */
public class UpdateGriffonSettingsAction extends MvcActionBase {

  @Override
  protected boolean isFrameworkSupported(@NotNull MvcFramework framework) {
    return framework == GriffonFramework.getInstance();
  }

  @Override
  protected void actionPerformed(@NotNull AnActionEvent e, @NotNull final Module module, @NotNull MvcFramework framework) {
    GriffonFramework.getInstance().updateProjectStructure(module);
  }

  @Override
  protected void updateView(AnActionEvent event, @NotNull MvcFramework framework, @NotNull Module module) {
    event.getPresentation().setIcon(AllIcons.Actions.Refresh);
  }

}
