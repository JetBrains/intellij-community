package org.jetbrains.plugins.gradle.diff.library;

import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.model.project.change.JarPresenceChange;
import com.intellij.openapi.externalSystem.model.project.id.JarId;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.id.LibraryId;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/3/12 11:17 AM
 */
public class GradleLibraryStructureChangesCalculator implements ExternalProjectStructureChangesCalculator<LibraryData, Library> {
  
  private final PlatformFacade myPlatformFacade;

  public GradleLibraryStructureChangesCalculator(@NotNull PlatformFacade platformFacade) {
    myPlatformFacade = platformFacade;
  }

  @Override
  public void calculate(@NotNull LibraryData gradleEntity,
                        @NotNull Library ideEntity,
                        @NotNull ExternalProjectChangesCalculationContext context)
  {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      doCalculate(gradleEntity, pathType, context.getLibraryPathTypeMapper().map(pathType), ideEntity, context);
    }
  }

  private void doCalculate(@NotNull LibraryData gradleEntity,
                           @NotNull LibraryPathType gradleType,
                           @NotNull OrderRootType ideType,
                           @NotNull Library ideEntity,
                           @NotNull ExternalProjectChangesCalculationContext context)
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
      // TODO den implement
//      LibraryId libraryId = new LibraryId(ProjectSystemId.GRADLE, gradleEntity.getName());
//      for (String path : gradleBinaryPaths) {
//        context.register(new JarPresenceChange(new JarId(path, gradleType, libraryId), null));
//      }
    }

    if (!ideBinaryPaths.isEmpty()) {
      // TODO den implement
//      LibraryId libraryId = new LibraryId(ProjectSystemId.IDE, GradleUtil.getLibraryName(ideEntity));
//      for (String path : ideBinaryPaths) {
//        context.register(new JarPresenceChange(null, new JarId(path, gradleType, libraryId)));
//      }
    }
  }

  // TODO den implement
//  @NotNull
//  @Override
//  public Object getIdeKey(@NotNull Library entity) {
//    return GradleUtil.getLibraryName(entity);
//  }
//
//  @NotNull
//  @Override
//  public Object getGradleKey(@NotNull LibraryData entity, @NotNull ExternalProjectChangesCalculationContext context) {
//    return entity.getName();
//  }
}
