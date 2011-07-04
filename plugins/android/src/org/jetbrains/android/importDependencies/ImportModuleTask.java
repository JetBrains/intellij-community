package org.jetbrains.android.importDependencies;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
* @author Eugene.Kudelevsky
*/
class ImportModuleTask extends ModuleProvidingTask {
  private final Project myProject;
  private final String myModuleFilePath;
  private final VirtualFile myContentRoot;

  public ImportModuleTask(@NotNull Project project,
                          @NotNull String moduleFilePath,
                          @NotNull VirtualFile contentRoot) {
    myModuleFilePath = moduleFilePath;
    myContentRoot = contentRoot;
    myProject = project;
  }

  @Override
  public Exception perform() {
    final Module[] moduleWrapper = {null};
    final Exception exception = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
      @Override
      public Exception compute() {
        try {
          moduleWrapper[0] = ModuleManager.getInstance(myProject).loadModule(myModuleFilePath);
        }
        catch (InvalidDataException e) {
          return e;
        }
        catch (IOException e) {
          return e;
        }
        catch (JDOMException e) {
          return e;
        }
        catch (ModuleWithNameAlreadyExists e) {
          return e;
        }
        return null;
      }
    });

    if (exception != null) {
      return exception;
    }
    assert moduleWrapper[0] != null;
    AndroidUtils.addAndroidFacetIfNecessary(moduleWrapper[0], myContentRoot, true);
    AndroidSdkUtils.setupAndroidPlatformInNeccessary(moduleWrapper[0]);
    setDepModule(moduleWrapper[0]);
    return null;
  }

  @NotNull
  @Override
  public String getTitle() {
    return AndroidBundle
      .message("android.import.dependencies.import.module.task.title", getModuleName(), FileUtil.toSystemDependentName(myModuleFilePath));
  }

  @Override
  public String getModuleName() {
    return FileUtil.getNameWithoutExtension(new File(myModuleFilePath).getName());
  }

  @NotNull
  @Override
  public VirtualFile getContentRoot() {
    return myContentRoot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ImportModuleTask that = (ImportModuleTask)o;

    if (!myContentRoot.equals(that.myContentRoot)) return false;
    if (!myModuleFilePath.equals(that.myModuleFilePath)) return false;
    if (!myProject.equals(that.myProject)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + myModuleFilePath.hashCode();
    result = 31 * result + myContentRoot.hashCode();
    return result;
  }
}
