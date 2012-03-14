package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.GradleDependencyImporter;
import org.jetbrains.plugins.gradle.importing.GradleLibraryImporter;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.util.*;

/**
 * Automatically configures groovy sdk for the modules that has '*.gradle' files.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/13/12 3:12 PM
 */
public class GradleGroovyEnabler extends AbstractProjectComponent {

  @NotNull private final PlatformFacade           myPlatformFacade;
  @NotNull private final GradleLibraryImporter    myLibraryImporter;
  @NotNull private final GradleDependencyImporter myDependencyImporter;
  
  public GradleGroovyEnabler(@NotNull Project project,
                             @NotNull PlatformFacade facade,
                             @NotNull GradleLibraryImporter libraryImporter,
                             @NotNull GradleDependencyImporter importer)
  {
    super(project);
    myPlatformFacade = facade;
    myLibraryImporter = libraryImporter;
    myDependencyImporter = importer;
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleConfigNotifier.TOPIC, new GradleConfigNotifierAdapter() {
      @Override
      public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath) {
        GradleGroovyEnabler.this.onGradleHomeChange(newPath);
      }
    });
  }

  public void onGradleHomeChange(@Nullable String newPath) {
    if (newPath == null) {
      return;
    }
    setupGroovySdkIfNecessary(newPath);
  }

  /**
   * Configures groovy sdk for the modules that require it (have gradle scripts but no groovy sdk).
   * 
   * @param gradleHome  gradle home to use
   * @return            groovy sdk library if the one has been created and configured;
   *                    <code>null</code> otherwise (no module requires groovy support or all target modules already have groovy sdk)
   */
  @Nullable
  public Library setupGroovySdkIfNecessary(@NotNull String gradleHome) {
    final Collection<Module> modules = getModulesThatRequireSupport();
    if (modules.isEmpty()) {
      return null;
    }
    final Library sdk = getGroovySdk(gradleHome);
    if (sdk == null) {
      return null;
    }
    for (Module module : modules) {
      applySdk(module, sdk);
    }
    return sdk;
  }

  /**
   * @return    all modules of the current project that have dedicated gradle config script but don't have groovy sdk
   */
  @NotNull
  private Collection<Module> getModulesThatRequireSupport() {
    final List<Module> result = new ArrayList<Module>();
    for (Module module : myPlatformFacade.getModules(myProject)) {
      // Skip module if it already has groovy support.
      if (LibrariesUtil.hasGroovySdk(module)) {
        continue;
      }

      // Skip module if it doesn't have a dedicated gradle script.
      boolean needGroovy = false;
      for (ModuleAwareContentRoot root : myPlatformFacade.getContentRoots(module)) {
        final VirtualFile file = root.getFile();
        if (!file.isDirectory()) {
          continue;
        }
        final VirtualFile child = file.findChild(GradleConstants.DEFAULT_SCRIPT_NAME);
        if (child != null) {
          needGroovy = true;
          break;
        }
      }
      if (needGroovy) {
        result.add(module);
      }
    }
    return result;
  }

  /**
   * Checks if there is groovy sdk configured at the project-level; configures the one to the groovy bundled by the gradle and returns it.
   * 
   * @param gradleHome  target gradle home to use for groovy sdk configuration
   * @return            groovy sdk library if it's possible to find/configure the one; <code>null</code> otherwise
   */
  @Nullable
  private Library getGroovySdk(@NotNull String gradleHome) {
    final Library[] libraries = GroovyConfigUtils.getInstance().getAllSDKLibraries(myProject);
    if (libraries != null && libraries.length > 0) {
      return libraries[0];
    }
    final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(gradleHome + "/lib/", GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
    if (groovyJars == null || groovyJars.length <= 0) {
      return null;
    }

    return myLibraryImporter.importLibrary("groovy", Collections.singletonMap(OrderRootType.CLASSES, Arrays.asList(groovyJars)), myProject);
  }
  
  private void applySdk(@NotNull Module module, @NotNull Library sdk) {
    myDependencyImporter.importLibraryDependencies(module, Collections.singleton(sdk));
  }
}
