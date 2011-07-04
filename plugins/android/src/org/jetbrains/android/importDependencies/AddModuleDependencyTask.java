package org.jetbrains.android.importDependencies;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
class AddModuleDependencyTask extends ImportDependenciesTask {
  private final ModuleProvider myModuleProvider;
  private final ModuleProvider myDepModuleProvider;

  public AddModuleDependencyTask(@NotNull ModuleProvider moduleProvider, @NotNull ModuleProvider depModuleProvider) {
    myModuleProvider = moduleProvider;
    myDepModuleProvider = depModuleProvider;
  }

  @Nullable
  @Override
  public Exception perform() {
    final Module module = myModuleProvider.getModule();
    final Module depModule = myDepModuleProvider.getModule();
    if (module == null || depModule == null) {
      return null;
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

    if (!rootManager.isDependsOn(depModule)) {
      final ModifiableRootModel model = rootManager.getModifiableModel();
      model.addModuleOrderEntry(depModule);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          model.commit();
        }
      });
    }
    return null;
  }

  @NotNull
  @Override
  public String getTitle() {
    return AndroidBundle.message("android.import.dependencies.add.module.dependency.task.title", myModuleProvider.getModuleName(),
                                 myDepModuleProvider.getModuleName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AddModuleDependencyTask that = (AddModuleDependencyTask)o;

    if (!myDepModuleProvider.equals(that.myDepModuleProvider)) return false;
    if (!myModuleProvider.equals(that.myModuleProvider)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myModuleProvider.hashCode();
    result = 31 * result + myDepModuleProvider.hashCode();
    return result;
  }
}
