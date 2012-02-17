package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/3/12 11:17 AM
 */
public class GradleLibraryStructureChangesCalculator implements GradleStructureChangesCalculator<GradleLibrary, Library> {
  
  private final PlatformFacade myPlatformFacade;

  public GradleLibraryStructureChangesCalculator(@NotNull PlatformFacade platformFacade) {
    myPlatformFacade = platformFacade;
  }

  @Override
  public void calculate(@NotNull GradleLibrary gradleEntity,
                        @NotNull Library intellijEntity,
                        @NotNull Set<GradleProjectStructureChange> knownChanges,
                        @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    final Set<String> gradleBinaryPaths = new HashSet<String>(gradleEntity.getPaths(LibraryPathType.BINARY));
    final Set<String> intellijBinaryPaths = new HashSet<String>();
    for (VirtualFile file : intellijEntity.getFiles(OrderRootType.CLASSES)) {
      final String path = myPlatformFacade.getLocalFileSystemPath(file);
      if (!gradleBinaryPaths.remove(path)) {
        intellijBinaryPaths.add(path);
      }
    }

    if (!gradleBinaryPaths.equals(intellijBinaryPaths)) {
      currentChanges.add(new GradleMismatchedLibraryPathChange(intellijEntity, gradleBinaryPaths, intellijBinaryPaths));
    }
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Library entity) {
    return entity.getName();
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleLibrary entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    // TODO den consider the known changes 
    return entity.getName();
  }
}
