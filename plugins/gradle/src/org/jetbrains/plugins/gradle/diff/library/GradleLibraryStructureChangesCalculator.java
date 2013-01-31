package org.jetbrains.plugins.gradle.diff.library;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId;
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
                        @NotNull Library ideEntity,
                        @NotNull GradleChangesCalculationContext context)
  {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      doCalculate(gradleEntity, pathType, context.getLibraryPathTypeMapper().map(pathType), ideEntity, context);
    }
  }

  private void doCalculate(@NotNull GradleLibrary gradleEntity,
                           @NotNull LibraryPathType gradleType,
                           @NotNull OrderRootType ideType,
                           @NotNull Library ideEntity,
                           @NotNull GradleChangesCalculationContext context)
  {
    final Set<String> gradleBinaryPaths = new HashSet<String>(gradleEntity.getPaths(gradleType));
    final Set<String> ideBinaryPaths = new HashSet<String>();
    for (VirtualFile file : ideEntity.getFiles(ideType)) {
      final String path = myPlatformFacade.getLocalFileSystemPath(file);
      if (!gradleBinaryPaths.remove(path)) {
        ideBinaryPaths.add(path);
      }
    }

    if (!gradleBinaryPaths.isEmpty()) {
      GradleLibraryId libraryId = new GradleLibraryId(GradleEntityOwner.GRADLE, gradleEntity.getName());
      for (String path : gradleBinaryPaths) {
        context.register(new GradleJarPresenceChange(new GradleJarId(path, gradleType, libraryId), null));
      }
    }

    if (!ideBinaryPaths.isEmpty()) {
      GradleLibraryId libraryId = new GradleLibraryId(GradleEntityOwner.IDE, GradleUtil.getLibraryName(ideEntity));
      for (String path : ideBinaryPaths) {
        context.register(new GradleJarPresenceChange(null, new GradleJarId(path, gradleType, libraryId)));
      }
    }
  }

  @NotNull
  @Override
  public Object getIdeKey(@NotNull Library entity) {
    return GradleUtil.getLibraryName(entity);
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleLibrary entity, @NotNull GradleChangesCalculationContext context) {
    // TODO den consider the known changes 
    return entity.getName();
  }
}
