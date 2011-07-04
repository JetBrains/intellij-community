package org.jetbrains.android.importDependencies;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.newProject.AndroidModuleType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
class CreateNewModuleTask extends ModuleProvidingTask {
  private final Project myProject;
  private final VirtualFile myContentRoot;

  public CreateNewModuleTask(@NotNull Project project, @NotNull VirtualFile contentRoot) {
    myContentRoot = contentRoot;
    myProject = project;
  }

  @Override
  public Exception perform() {
    final Module depModule = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        final Module depModule =
          ModuleManager.getInstance(myProject)
            .newModule(myContentRoot.getPath() + '/' + myContentRoot.getName() + ".iml", StdModuleTypes.JAVA);
        final ModifiableRootModel model = ModuleRootManager.getInstance(depModule).getModifiableModel();
        model.addContentEntry(myContentRoot);
        model.commit();
        return depModule;
      }
    });
    AndroidUtils.addAndroidFacetIfNecessary(depModule, myContentRoot, true);
    AndroidSdkUtils.setupAndroidPlatformInNeccessary(depModule);
    setDepModule(depModule);
    return null;
  }

  @NotNull
  @Override
  public String getTitle() {
    final String contentRootPath = FileUtil.toSystemDependentName(myContentRoot.getPath());
    return AndroidBundle.message("android.import.dependencies.new.module.task.title", getModuleName(), contentRootPath);
  }

  @Override
  public String getModuleName() {
    return myContentRoot.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CreateNewModuleTask that = (CreateNewModuleTask)o;

    if (!myContentRoot.equals(that.myContentRoot)) return false;
    if (!myProject.equals(that.myProject)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + myContentRoot.hashCode();
    return result;
  }

  @NotNull
  public VirtualFile getContentRoot() {
    return myContentRoot;
  }
}
