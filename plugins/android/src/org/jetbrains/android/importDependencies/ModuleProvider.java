package org.jetbrains.android.importDependencies;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
abstract class ModuleProvider {

  private ModuleProvider() {
  }

  @Nullable
  public abstract Module getModule();

  @NotNull
  public abstract String getModuleName();

  public static ModuleProvider create(@NotNull final ModuleProvidingTask task) {
    return new MyNewModuleProvider(task);
  }

  public static ModuleProvider create(@NotNull final Module module) {
    return new MyExistingModuleProvider(module);
  }

  private static class MyNewModuleProvider extends ModuleProvider {
    private final ModuleProvidingTask myTask;

    public MyNewModuleProvider(@NotNull ModuleProvidingTask task) {
      myTask = task;
    }

    @Override
    public Module getModule() {
      return myTask.getDepModule();
    }

    @NotNull
    @Override
    public String getModuleName() {
      return myTask.getModuleName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyNewModuleProvider that = (MyNewModuleProvider)o;

      if (!myTask.equals(that.myTask)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myTask.hashCode();
    }
  }

  private static class MyExistingModuleProvider extends ModuleProvider {
    private final Module myModule;

    public MyExistingModuleProvider(Module module) {
      myModule = module;
    }

    @Override
    public Module getModule() {
      return myModule;
    }

    @NotNull
    @Override
    public String getModuleName() {
      return myModule.getName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyExistingModuleProvider that = (MyExistingModuleProvider)o;

      if (!myModule.equals(that.myModule)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myModule.hashCode();
    }
  }
}
