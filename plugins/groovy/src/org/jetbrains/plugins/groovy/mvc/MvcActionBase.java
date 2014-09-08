/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MvcActionBase extends DumbAwareAction {

  protected abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull Module module, @NotNull MvcFramework framework);
  
  @Override
  public final void actionPerformed(AnActionEvent e) {
    Pair<MvcFramework, Module> pair = guessFramework(e);
    if (pair != null && isSupported(pair.getFirst(), pair.getSecond())) {
      actionPerformed(e, pair.getSecond(), pair.getFirst());
    }
  }

  protected boolean isFrameworkSupported(@NotNull MvcFramework framework) {
    return true;
  }

  protected boolean isSupported(@NotNull MvcFramework framework, @NotNull Module module) {
    return isFrameworkSupported(framework);
  }

  @Nullable
  public static Pair<MvcFramework, Module> guessFramework(AnActionEvent event) {
    final Module module = event.getData(
      ActionPlaces.isMainMenuOrActionSearch(event.getPlace()) ? LangDataKeys.MODULE : LangDataKeys.MODULE_CONTEXT);

    if (module != null) {
      MvcFramework commonPluginModuleFramework = MvcFramework.findCommonPluginModuleFramework(module);

      if (commonPluginModuleFramework != null) {
        for (Module mod : ModuleManager.getInstance(module.getProject()).getModules()) {
          if (commonPluginModuleFramework.getCommonPluginsModuleName(mod).equals(module.getName())) {
            if (commonPluginModuleFramework.hasSupport(mod)) {
              return Pair.create(commonPluginModuleFramework, mod);
            }

            return null;
          }
        }
      }

      MvcFramework framework = MvcFramework.getInstance(module);
      if (framework != null) {
        return Pair.create(framework, module);
      }
    }

    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      return null;
    }

    Pair<MvcFramework, Module> result = null;
    for (Module mod : ModuleManager.getInstance(project).getModules()) {
      final MvcFramework framework = MvcFramework.getInstance(mod);
      if (framework != null) {
        if (result != null) {
          return null;
        }
        result = Pair.create(framework, mod);
      }
    }

    return result;
  }

  @Override
  public final void update(AnActionEvent event) {
    Pair<MvcFramework, Module> pair = guessFramework(event);
    if (pair != null && isSupported(pair.getFirst(), pair.getSecond())) {
      event.getPresentation().setVisible(true);
      updateView(event, pair.getFirst(), pair.getSecond());
    }
    else {
      event.getPresentation().setVisible(false);
    }
  }

  protected void updateView(AnActionEvent event, @NotNull MvcFramework framework, @NotNull Module module) {

  }
}
