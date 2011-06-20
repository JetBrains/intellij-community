package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
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
    if (pair != null && isSupportFramework(pair.getFirst())) {
      actionPerformed(e, pair.getSecond(), pair.getFirst());
    }
  }

  protected boolean isSupportFramework(@NotNull MvcFramework framework) {
    return true;
  }

  @Nullable
  public static Pair<MvcFramework, Module> guessFramework(AnActionEvent event) {
    final Module module = event.getData(event.getPlace() == ActionPlaces.MAIN_MENU ? DataKeys.MODULE : DataKeys.MODULE_CONTEXT);

    if (module != null) {
      MvcFramework commonPluginModuleFramework = MvcFramework.findCommonPluginModuleFramework(module);

      if (commonPluginModuleFramework != null) {
        for (Module mod : ModuleManager.getInstance(module.getProject()).getModules()) {
          if (commonPluginModuleFramework.getCommonPluginsModuleName(mod).equals(module.getName())) {
            if (commonPluginModuleFramework.hasSupport(mod)) {
              return new Pair<MvcFramework, Module>(commonPluginModuleFramework, mod);
            }

            return null;
          }
        }
      }

      MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(module);
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
      final MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(mod);
      if (framework != null) {
        if (result != null) {
          return null;
        }
        result = Pair.create(framework, mod);
      }
    }

    return result;
  }

  public final void update(AnActionEvent event) {
    Pair<MvcFramework, Module> pair = guessFramework(event);
    if (pair != null && isSupportFramework(pair.getFirst())) {
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
