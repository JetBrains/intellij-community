package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleInstallationManager;
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
    return super.hasFrameworkLibrary(module) || myLibraryManager.getAllLibraries(module.getProject()) != null;
  }
}
