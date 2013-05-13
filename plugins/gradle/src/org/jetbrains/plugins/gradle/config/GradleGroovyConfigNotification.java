package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.groovy.annotator.DefaultGroovyFrameworkConfigNotification;

/**
 * @author Denis Zhdanov
 * @since 3/19/12 4:07 PM
 */
public class GradleGroovyConfigNotification extends DefaultGroovyFrameworkConfigNotification {

  @NotNull private final PlatformFacade            myPlatformFacade;
  @NotNull private final GradleInstallationManager myLibraryManager;

  public GradleGroovyConfigNotification(@NotNull PlatformFacade facade, @NotNull GradleInstallationManager libraryManager) {
    myPlatformFacade = facade;
    myLibraryManager = libraryManager;
  }

  @Override
  public boolean hasFrameworkStructure(@NotNull Module module) {
    for (ModuleAwareContentRoot root : myPlatformFacade.getContentRoots(module)) {
      final VirtualFile file = root.getFile();
      if (!file.isDirectory()) {
        continue;
      }
      final VirtualFile child = file.findChild(GradleConstants.DEFAULT_SCRIPT_NAME);
      if (child != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasFrameworkLibrary(@NotNull Module module) {
    // Expecting groovy library to always be available at the gradle distribution. I.e. consider that when correct gradle
    // distribution is defined for the project, groovy jar is there.
    if (super.hasFrameworkLibrary(module)) {
      return true;
    }
    String linkedProjectPath = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
    if (StringUtil.isEmpty(linkedProjectPath)) {
      return false;
    }
    assert linkedProjectPath != null;
    return myLibraryManager.getAllLibraries(module.getProject(), linkedProjectPath) != null;
  }
}
