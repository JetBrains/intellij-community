// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxModuleUtil {
  public static boolean isInJavaFxProject(@NotNull PsiFile file) {
    final Project project = file.getProject();
    if (hasJavaFxArtifacts(project)) {
      return true;
    }
    return isInJavaFxModule(file);
  }

  private static boolean isInJavaFxModule(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      final Project project = file.getProject();
      final Module fileModule = ModuleUtilCore.findModuleForFile(virtualFile, project);
      if (fileModule != null) {
        return getCachedJavaFxModules(project).contains(fileModule);
      }
    }
    return false;
  }

  @NotNull
  private static Set<Module> getCachedJavaFxModules(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(
      project, () -> {
        final Collection<VirtualFile> files =
          FileTypeIndex.getFiles(JavaFxFileTypeFactory.getFileType(), GlobalSearchScope.projectScope(project));

        final Set<Module> modules = files.stream()
          .filter(JavaFxFileTypeFactory::isFxml)
          .map(file -> ModuleUtilCore.findModuleForFile(file, project))
          .collect(Collectors.toCollection(THashSet::new));

        return CachedValueProvider.Result.create(modules, FxmlPresenceListener.getModificationTracker(project));
      });
  }

  private static boolean hasJavaFxArtifacts(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(
      project, () -> {
        final ArtifactManager artifactManager = ArtifactManager.getInstance(project);
        final Collection<? extends Artifact> artifacts = artifactManager.getArtifactsByType(JavaFxApplicationArtifactType.getInstance());
        return CachedValueProvider.Result.create(!artifacts.isEmpty(), artifactManager.getModificationTracker());
      });
  }

  /**
   * Avoids freeze on first use of Java intentions
   */
  public static class JavaFxDetectionStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }
      StartupManager.getInstance(project).runWhenProjectIsInitialized(
        () -> ApplicationManager.getApplication().executeOnPooledThread(
          () -> DumbService.getInstance(project).runReadActionInSmartMode(
            () -> populateCachedJavaFxModules(project))));
    }

    private static void populateCachedJavaFxModules(@NotNull Project project) {
      if (!project.isDisposed() && project.isOpen()) {
        hasJavaFxArtifacts(project);
        getCachedJavaFxModules(project);
      }
    }
  }
}
