// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
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
      e.getPresentation().setText(GroovyBundle.message("mvc.framework.0.run.target.action.text", pair.first.getDisplayName()));
      e.getPresentation().setDescription(GroovyBundle.message("mvc.framework.0.run.target.action.description", pair.first.getDisplayName()));
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
