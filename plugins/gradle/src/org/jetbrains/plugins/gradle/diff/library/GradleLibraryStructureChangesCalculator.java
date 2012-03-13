package org.jetbrains.plugins.gradle.diff.library;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.util.GradleUtil;

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
                        @NotNull GradleChangesCalculationContext context)
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
      context.register(new GradleMismatchedLibraryPathChange(intellijEntity, gradleBinaryPaths, intellijBinaryPaths));
    }
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Library entity) {
    return GradleUtil.getLibraryName(entity);
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleLibrary entity, @NotNull GradleChangesCalculationContext context) {
    // TODO den consider the known changes 
    return entity.getName();
  }
}
