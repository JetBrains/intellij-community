/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.*;

/**
 * @author ilyas
 */
public class DefaultMvcRunCommandActionHandler extends MvcRunCommandActionHandler {

  @Override
  public boolean isAvailable(@NotNull AnActionEvent e) {
    final Pair<MvcFramework, Module> pair = MvcActionBase.guessFramework(e);
    return pair != null && pair.first.isRunTargetActionSupported(pair.second);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Pair<MvcFramework, Module> pair = MvcActionBase.guessFramework(e);
    if (pair != null) {
      e.getPresentation().setText("Run " + pair.first.getDisplayName() + " Target");
      e.getPresentation().setDescription("Run arbitrary " + pair.first.getDisplayName() + " target");
    }
  }

  @Override
  public void performAction(@NotNull AnActionEvent e) {
    Pair<MvcFramework, Module> pair = MvcActionBase.guessFramework(e);
    if (pair == null) return;

    MvcFramework framework = pair.first;
    Module module = pair.second;
    MvcRunTargetDialog dialog = new MvcRunTargetDialog(module, framework);
    if (!dialog.showAndGet()) {
      return;
    }

    Module selectedModule = dialog.getSelectedModule();
    MvcCommand cmd = dialog.createCommand();
    MvcCommandExecutor.run(selectedModule, framework, cmd, null, false);
  }
}
