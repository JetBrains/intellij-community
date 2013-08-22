package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.annotator.DefaultGroovyFrameworkConfigNotification;

/**
 * @author Denis Zhdanov
 * @since 3/19/12 4:07 PM
 */
public class GradleGroovyConfigNotification extends DefaultGroovyFrameworkConfigNotification {

  @NotNull private final GradleInstallationManager myLibraryManager;

  public GradleGroovyConfigNotification(@NotNull GradleInstallationManager libraryManager) {
    myLibraryManager = libraryManager;
  }

  @Override
  public boolean hasFrameworkStructure(@NotNull Module module) {
    String path = getConfigPath(module);
    if (path == null) {
      return false;
    }

    VirtualFile configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    return configDir != null && configDir.isDirectory();
  }

  @Override
  public boolean hasFrameworkLibrary(@NotNull Module module) {
    // Expecting groovy library to always be available at the gradle distribution. I.e. consider that when correct gradle
    // distribution is defined for the project, groovy jar is there.
    if (super.hasFrameworkLibrary(module)) {
      return true;
    }
    String linkedProjectPath = getConfigPath(module);
    if (StringUtil.isEmpty(linkedProjectPath)) {
      return false;
    }
    assert linkedProjectPath != null;
    return myLibraryManager.getAllLibraries(module.getProject(), linkedProjectPath) != null;
  }

  @Nullable
  private static String getConfigPath(@NotNull Module module) {
    String externalSystemId = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
    if (externalSystemId == null || !GradleConstants.SYSTEM_ID.toString().equals(externalSystemId)) {
      return null;
    }

    String path = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
    return StringUtil.isEmpty(path) ? null : path;
  }
}
