package org.jetbrains.android.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPostcompileTask implements CompileTask {
  @Override
  public boolean execute(final CompileContext context) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        deleteObsoleteGenModules(context.getProject());
      }
    });
    return true;
  }

  private static void deleteObsoleteGenModules(@NotNull Project project) {
    final Set<Module> toDelete = new HashSet<Module>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (AndroidCompileUtil.isGenModule(module)) {
        final Module baseModule = AndroidCompileUtil.getBaseModuleByGenModule(module);

        if (baseModule == null) {
          toDelete.add(module);
        }
      }
    }

    if (toDelete.size() == 0) {
      return;
    }

    final ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();

    for (Module module : toDelete) {
      model.disposeModule(module);
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }
}
