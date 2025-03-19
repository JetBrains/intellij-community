// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findChild;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.*;

/**
 * {@link LibraryDataNodeSubstitutor} provides the facility to replace library dependencies with the related module dependencies
 * based on artifacts and source compilation output mapping
 */
@ApiStatus.Internal
public class LibraryDataNodeSubstitutor {
  private final @NotNull ProjectResolverContext resolverContext;
  private final @Nullable File gradleUserHomeDir;
  private final @Nullable File gradleHomeDir;
  private final @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap;
  private final @NotNull Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap;
  private final @NotNull ArtifactMappingService artifactsMap;

  public LibraryDataNodeSubstitutor(@NotNull ProjectResolverContext context,
                                    @Nullable File gradleUserHomeDir,
                                    @Nullable File gradleHomeDir,
                                    @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                    @NotNull Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap,
                                    @NotNull ArtifactMappingService artifactsMap) {
    resolverContext = context;
    this.gradleUserHomeDir = gradleUserHomeDir;
    this.gradleHomeDir = gradleHomeDir;
    this.sourceSetMap = sourceSetMap;
    this.moduleOutputsMap = moduleOutputsMap;
    this.artifactsMap = artifactsMap;
  }

  public void run(@NotNull DataNode<LibraryDependencyData> libraryDependencyDataNode) {
    final DataNode<?> libraryNodeParent = libraryDependencyDataNode.getParent();
    if (libraryNodeParent == null) return;

    final LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();
    final LibraryData libraryData = libraryDependencyData.getTarget();
    final Set<String> libraryPaths = libraryData.getPaths(LibraryPathType.BINARY);
    if (libraryPaths.isEmpty()) return;

    if (StringUtil.isNotEmpty(libraryData.getExternalName())) {
      if (gradleUserHomeDir != null) {
        attachSourcesAndJavadocFromGradleCacheIfNeeded(resolverContext, gradleUserHomeDir, libraryData);
      }
      return;
    }

    boolean shouldKeepTransitiveDependencies = !libraryDependencyDataNode.getChildren().isEmpty();
    Collection<AbstractDependencyData<?>> dependenciesToShift = collectDependenciesOrderedAfter(libraryNodeParent, libraryDependencyData.getOrder());

    int classpathOrderShift = -1;
    final ArrayDeque<String> unprocessedPaths = new ArrayDeque<>(libraryPaths);
    while (!unprocessedPaths.isEmpty()) {
      final String path = unprocessedPaths.remove();
      Collection<ModuleLookupResult> lookupResults = lookupTargetModule(path);
      for (ModuleLookupResult result : lookupResults) {
        if (createAndMaybeAttachNewModuleDependency(libraryDependencyDataNode, result, libraryPaths,
                                                    shouldKeepTransitiveDependencies,
                                                    unprocessedPaths, classpathOrderShift, path)) {
          classpathOrderShift++;
        }
      }

      ModuleMappingInfo mapping = resolverContext.getArtifactsMap().getModuleMapping(path);
      if (!lookupResults.isEmpty() && (mapping == null || !mapping.getHasNonModulesContent())) {
        libraryPaths.remove(path);
      }
      if (libraryPaths.isEmpty()) {
        libraryDependencyDataNode.clear(true);
        break;
      }
    }

    applyClasspathShift(dependenciesToShift, classpathOrderShift);

    if (libraryDependencyDataNode.getParent() != null) {
      if (libraryPaths.size() > 1) {
        List<String> toRemove = new SmartList<>();
        for (String path : libraryPaths) {
          final File binaryPath = new File(path);
          if (binaryPath.isFile()) {
            final LibraryData extractedLibrary = new LibraryData(libraryDependencyData.getOwner(), "");
            extractedLibrary.addPath(LibraryPathType.BINARY, path);
            var gradleVersion = resolverContext.getProjectGradleVersion();
            if (gradleHomeDir != null && gradleVersion != null) {
              attachGradleSdkSources(binaryPath, extractedLibrary, gradleHomeDir, gradleVersion);
            }
            LibraryDependencyData extractedDependencyData = new LibraryDependencyData(
              libraryDependencyData.getOwnerModule(), extractedLibrary, LibraryLevel.MODULE);
            libraryDependencyDataNode.getParent().createChild(ProjectKeys.LIBRARY_DEPENDENCY, extractedDependencyData);

            toRemove.add(path);
          }
        }
        libraryPaths.removeAll(toRemove);
        if (libraryPaths.isEmpty()) {
          libraryDependencyDataNode.clear(true);
        }
      }
    }
  }

  private static Collection<AbstractDependencyData<?>> collectDependenciesOrderedAfter(@NotNull DataNode<?> parent, int order) {
    return ContainerUtil.mapNotNull(parent.getChildren(), it -> {
        if (it.getData() instanceof AbstractDependencyData<?> depData && depData.getOrder() > order) {
          return depData;
        }
        return null;
    });
  }

  private static boolean createAndMaybeAttachNewModuleDependency(@NotNull DataNode<LibraryDependencyData> libraryDependencyDataNode,
                                                                 @NotNull ModuleLookupResult lookupResult,
                                                                 @NotNull Set<String> libraryPaths,
                                                                 boolean shouldKeepTransitiveDependencies,
                                                                 @NotNull ArrayDeque<String> unprocessedPaths,
                                                                 int classpathOrderShift, String path) {

    boolean addedNewDependency = false;
    LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();
    DataNode<?> libraryNodeParent = libraryDependencyDataNode.getParent();
    if (libraryNodeParent == null) {
      return addedNewDependency;
    }

    DataNode<GradleSourceSetData> targetSourceSetNode = lookupResult.sourceSetDataDataNode();
    ExternalSourceSet targetExternalSourceSet = lookupResult.externalSourceSet();
    final ModuleData targetModuleData = targetSourceSetNode.getData();

    Set<String> targetModuleOutputPaths = collectTargetModuleOutputPaths(libraryPaths,
                                                               targetSourceSetNode.getUserData(GradleProjectResolver.GRADLE_OUTPUTS));

    final ModuleData ownerModule = libraryDependencyData.getOwnerModule();
    final ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, targetModuleData);
    moduleDependencyData.setScope(libraryDependencyData.getScope());
    moduleDependencyData.setOrder(libraryDependencyData.getOrder() + classpathOrderShift + 1);
    moduleDependencyData.setModuleDependencyArtifacts(Collections.singleton(path));

    if (targetExternalSourceSet != null && isTestSourceSet(targetExternalSourceSet)) {
      moduleDependencyData.setProductionOnTestDependency(true);
    }
    final DataNode<ModuleDependencyData> found = findChild(
      libraryNodeParent, ProjectKeys.MODULE_DEPENDENCY, node -> {
        ModuleDependencyData candidateData = node.getData();

        if (!moduleDependencyData.getInternalName().equals(candidateData.getInternalName())) {
          return false;
        }
        // re-use module dependency artifacts even if the rest does not match
        moduleDependencyData.setModuleDependencyArtifacts(candidateData.getModuleDependencyArtifacts());

        final boolean result;
        // ignore provided scope during the search since it can be resolved incorrectly for file dependencies on a source set outputs
        // ignore scope if the candidate already has the largest possible scope - compile
        if (moduleDependencyData.getScope() == DependencyScope.PROVIDED ||
            candidateData.getScope() == DependencyScope.COMPILE) {
          result = isEqualIgnoringScope(moduleDependencyData, candidateData);
        }
        else {
          result = moduleDependencyData.equals(candidateData);
        }
        return result;
      });

    if (found == null) {
      DataNode<ModuleDependencyData> moduleDependencyNode =
        libraryNodeParent.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
      addedNewDependency = true;
      if (shouldKeepTransitiveDependencies) {
        for (DataNode<?> node : libraryDependencyDataNode.getChildren()) {
          moduleDependencyNode.addChild(node);
        }
      }
    }

    if (targetModuleOutputPaths != null) {
      unprocessedPaths.removeAll(targetModuleOutputPaths);
      libraryPaths.removeAll(targetModuleOutputPaths);
    }
    return addedNewDependency;
  }

  private static boolean isEqualIgnoringScope(@NotNull ModuleDependencyData data1, @NotNull ModuleDependencyData data2) {
    final boolean result;
    final DependencyScope tmp = data1.getScope();
    data1.setScope(data2.getScope());
    result = data1.equals(data2);
    data1.setScope(tmp);
    return result;
  }

  private @NotNull Collection<ModuleLookupResult> lookupTargetModule(String path) {
    List<ModuleLookupResult> results = new ArrayList<>();

    GradleSourceSetData targetModule = Optional.of(resolverContext.getSettings())
      .map(GradleExecutionSettings::getExecutionWorkspace)
      .map(ws -> ws.findModuleDataByArtifacts(Collections.singleton(new File(path))))
      .filter(md -> md instanceof GradleSourceSetData)
      .map(GradleSourceSetData.class::cast)
      .orElse(null);

    if (targetModule != null) {
      return Collections.singleton(new ModuleLookupResult(new DataNode<>(GradleSourceSetData.KEY, targetModule, null),
                                    null));
    }

    final String moduleId;
    final Pair<String, ExternalSystemSourceType> sourceTypePair = moduleOutputsMap.get(path);
    if (sourceTypePair != null) {
      moduleId = sourceTypePair.first;
      final Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> pair = sourceSetMap.get(moduleId);
      if (pair != null) {
        return Collections.singleton(new ModuleLookupResult(pair.first, pair.second));
      }
    }

    ModuleMappingInfo mapping = artifactsMap.getModuleMapping(path);
    if (mapping != null) {
      for (String id : mapping.getModuleIds()) {
        final Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> pair = sourceSetMap.get(id);
        if (pair != null) {
          results.add(new ModuleLookupResult(pair.first, pair.second));
        }
      }
    }

    return results;
  }


  private static void applyClasspathShift(@NotNull Collection<AbstractDependencyData<?>> dependenciesToMove, int shift) {
    if (shift > 0) {
      dependenciesToMove.forEach(data -> {
        int order = data.getOrder();
        data.setOrder(order + shift);
      });
    }
  }

  private static @Nullable Set<String> collectTargetModuleOutputPaths(@NotNull Set<String> libraryPaths,
                                                                      @Nullable MultiMap<ExternalSystemSourceType, String> gradleOutputs) {
    if (gradleOutputs == null) {
      return null;
    }

    final Set<String> compileSet = new HashSet<>();
    ContainerUtil.addAllNotNull(compileSet,
                                gradleOutputs.get(ExternalSystemSourceType.SOURCE));
    ContainerUtil.addAllNotNull(compileSet,
                                gradleOutputs.get(ExternalSystemSourceType.RESOURCE));
    if (ContainerUtil.intersects(libraryPaths, compileSet)) {
      return compileSet;
    }

    final Set<String> testSet = new HashSet<>();
    ContainerUtil.addAllNotNull(testSet,
                                gradleOutputs.get(ExternalSystemSourceType.TEST));
    ContainerUtil.addAllNotNull(testSet,
                                gradleOutputs.get(ExternalSystemSourceType.TEST_RESOURCE));
    if (ContainerUtil.intersects(libraryPaths, testSet)) {
      return testSet;
    }
    return null;
  }
}

record ModuleLookupResult(@NotNull DataNode<GradleSourceSetData> sourceSetDataDataNode,
                          @Nullable ExternalSourceSet externalSourceSet) {
}