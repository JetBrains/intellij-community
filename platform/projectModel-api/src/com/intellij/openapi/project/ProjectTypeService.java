// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

@Service(Service.Level.PROJECT)
@State(name = "ProjectType")
public final class ProjectTypeService implements PersistentStateComponent<ProjectType> {

  private static final Key<CachedValue<Collection<ProjectType>>> PROJECT_TYPES_KEY = Key.create("PROJECT_TYPES");

  private ProjectType myProjectType;

  /**
   * @deprecated Use {@link #getProjectTypes(Project)} instead
   */
  @Deprecated
  public static @Nullable ProjectType getProjectType(@Nullable Project project) {
    if (project != null) {
      ProjectType projectType = getInstance(project).myProjectType;
      if (projectType != null) return projectType;
    }

    Collection<ProjectType> projectTypes = getProjectTypes(project);
    if (!projectTypes.isEmpty()) return projectTypes.iterator().next();

    return null;
  }

  public static Set<String> getProjectTypeIds(@Nullable Project project) {
    return ContainerUtil.map2Set(getProjectTypes(project), ProjectType::getId);
  }

  public static Collection<ProjectType> getProjectTypes(@Nullable Project project) {
    if (project == null) return emptyList();
    if (project.isDefault()) return emptyList();

    return CachedValuesManager.getManager(project).getCachedValue(project, PROJECT_TYPES_KEY, () -> {
      return Result.create(findProjectTypes(project),
                           ProjectRootManager.getInstance(project),
                           DumbService.getInstance(project));
    }, false);
  }

  private static Collection<ProjectType> findProjectTypes(@NotNull Project project) {
    List<ProjectTypesProvider> providers = ProjectTypesProvider.EP_NAME.getExtensionList();
    if (providers.isEmpty()) return emptyList();

    return ReadAction.compute(() -> {
      if (DumbService.isDumb(project)) return emptyList();

      return providers.stream()
        .flatMap(p -> p.inferProjectTypes(project).stream())
        .toList();
    });
  }

  public static void setProjectType(@NotNull Project project, @NotNull ProjectType projectType) {
    getInstance(project).loadState(projectType);
  }

  private static ProjectTypeService getInstance(@NotNull Project project) {
    return project.getService(ProjectTypeService.class);
  }

  @Override
  public @Nullable ProjectType getState() {
    return myProjectType;
  }

  @Override
  public void loadState(@NotNull ProjectType state) {
    myProjectType = state;
  }

  @TestOnly
  public static void clearFieldsForLightProjectInTests(@NotNull Project project) {
    getInstance(project).myProjectType = null;
  }
}
