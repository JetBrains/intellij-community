package org.jetbrains.android.importDependencies;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
abstract class ModuleProvidingTask extends ImportDependenciesTask {
  private Module myDepModule;

  public Module getDepModule() {
    return myDepModule;
  }

  protected void setDepModule(Module depModule) {
    myDepModule = depModule;
  }

  public abstract String getModuleName();

  @NotNull
  public abstract VirtualFile getContentRoot();
}
