// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;

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
  private @NotNull final ProjectResolverContext resolverContext;
  private @Nullable final File gradleUserHomeDir;
  private @Nullable final File gradleHomeDir;
  private @Nullable final GradleVersion gradleVersion;
  private @NotNull final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap;
  private @NotNull final Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap;
  private @NotNull final ArtifactMappingService artifactsMap;

  public LibraryDataNodeSubstitutor(@NotNull ProjectResolverContext context,
                                    @Nullable File gradleUserHomeDir,
                                    @Nullable File gradleHomeDir,
                                    @Nullable GradleVersion gradleVersion,
                                    @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                    @NotNull Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap,
                                    @NotNull ArtifactMappingService artifactsMap) {
    resolverContext = context;
    this.gradleUserHomeDir = gradleUserHomeDir;
    this.gradleHomeDir = gradleHomeDir;
    this.gradleVersion = gradleVersion;
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

    boolean shouldKeepTransitiveDependencies = !libraryPaths.isEmpty() && !libraryDependencyDataNode.getChildren().isEmpty();
    Collection<AbstractDependencyData<?>> dependenciesToShift = collectDependenciesOrderedAfter(libraryNodeParent, libraryDependencyData.getOrder());

    int classpathOrderShift = -1;
    final ArrayDeque<String> unprocessedPaths = new ArrayDeque<>(libraryPaths);
    while (!unprocessedPaths.isEmpty()) {
      final String path = unprocessedPaths.remove();
      ModuleLookupResult lookupResult = lookupTargetModule(path);
      if (lookupResult == null) {
        continue;
      }

      if (createAndMaybeAttachNewModuleDependency(libraryDependencyDataNode, lookupResult, libraryPaths,
                                              shouldKeepTransitiveDependencies,
                                              unprocessedPaths, path)) {
        classpathOrderShift++;
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
                                @NotNull String path) {

    boolean addedNewDependency = false;
    LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();
    DataNode<?> libraryNodeParent = libraryDependencyDataNode.getParent();
    if (libraryNodeParent == null) {
      return addedNewDependency;
    }
    Set<String> targetModuleOutputPaths;
    DataNode<GradleSourceSetData> targetSourceSetNode = lookupResult.sourceSetDataDataNode();
    ExternalSourceSet targetExternalSourceSet = lookupResult.externalSourceSet();
    final ModuleData targetModuleData = targetSourceSetNode.getData();


    if (lookupResult.targetModuleOutputPaths() != null) {
      targetModuleOutputPaths = lookupResult.targetModuleOutputPaths();
    } else {
      targetModuleOutputPaths = collectTargetModuleOutputPaths(libraryPaths,
                                                               targetSourceSetNode.getUserData(GradleProjectResolver.GRADLE_OUTPUTS));
    }

    final ModuleData ownerModule = libraryDependencyData.getOwnerModule();
    final ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, targetModuleData);
    moduleDependencyData.setScope(libraryDependencyData.getScope());
    moduleDependencyData.setOrder(libraryDependencyData.getOrder());

    if (isTestSourceSet(targetExternalSourceSet)) {
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
        if (moduleDependencyData.getScope() == DependencyScope.PROVIDED) {
          moduleDependencyData.setScope(candidateData.getScope());
          result = moduleDependencyData.equals(candidateData);
          moduleDependencyData.setScope(DependencyScope.PROVIDED);
        }
        else {
          result = moduleDependencyData.equals(candidateData);
        }
        return result;
      });

    if (targetModuleOutputPaths != null) {
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
      unprocessedPaths.removeAll(targetModuleOutputPaths);
      libraryPaths.removeAll(targetModuleOutputPaths);
    }
    else {
      // do not add the path as library dependency if another module dependency is already contain the path as one of its output paths
      if (found != null) {
        libraryPaths.remove(path);
      }
    }
    return addedNewDependency;
  }

  private ModuleLookupResult lookupTargetModule(String path) {
    Set<String> targetModuleOutputPaths = null;

    final String moduleId;
    final Pair<String, ExternalSystemSourceType> sourceTypePair = moduleOutputsMap.get(path);
    if (sourceTypePair == null) {
      ModuleMappingInfo mapping = artifactsMap.getModuleMapping(path);
      moduleId = mapping != null ? mapping.getModuleId() : null;
      if (moduleId != null) {
        targetModuleOutputPaths = Set.of(path);
      }
    }
    else {
      moduleId = sourceTypePair.first;
    }
    if (moduleId == null) return null;

    final Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> pair = sourceSetMap.get(moduleId);
    if (pair == null) {
      return null;
    }
    return new ModuleLookupResult(targetModuleOutputPaths, pair.first, pair.second);
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

record ModuleLookupResult(Set<String> targetModuleOutputPaths, DataNode<GradleSourceSetData> sourceSetDataDataNode,
                          ExternalSourceSet externalSourceSet) {
}