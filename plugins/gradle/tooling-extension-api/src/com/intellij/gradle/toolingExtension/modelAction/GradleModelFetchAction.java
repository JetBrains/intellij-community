// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.internal.TurnOffDefaultTasks;
import org.jetbrains.plugins.gradle.tooling.serialization.ModelConverter;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Part of {@link ProjectImportAction} which fully executes on Gradle side.
 */
@ApiStatus.Internal
public class GradleModelFetchAction {

  private final @NotNull ProjectImportAction.AllModels myAllModels;

  private final @NotNull Set<ProjectImportModelProvider> myModelProviders;
  private final @NotNull ModelConverter myModelConverter;
  private final @NotNull ExecutorService myModelConverterExecutor;

  private final boolean myIsPreviewMode;
  private final boolean myIsProjectsLoadedAction;

  public GradleModelFetchAction(
    @NotNull ProjectImportAction.AllModels allModels,
    @NotNull Set<ProjectImportModelProvider> modelProviders,
    @NotNull ModelConverter modelConverter,
    @NotNull ExecutorService modelConverterExecutor,
    boolean isPreviewMode,
    boolean isProjectsLoadedAction
  ) {
    myAllModels = allModels;

    myModelProviders = modelProviders;
    myModelConverter = modelConverter;
    myModelConverterExecutor = modelConverterExecutor;

    myIsPreviewMode = isPreviewMode;
    myIsProjectsLoadedAction = isProjectsLoadedAction;
  }

  public void execute(@NotNull DefaultBuildController controller) {
    GradleBuild mainGradleBuild = controller.getBuildModel();

    //We only need these later, but need to fetch them before fetching other models because of https://github.com/gradle/gradle/issues/20008
    Set<GradleBuild> nestedBuilds = getNestedBuilds(controller, mainGradleBuild);

    addModels(controller, mainGradleBuild);
    for (GradleBuild includedBuild : nestedBuilds) {
      if (!myIsProjectsLoadedAction) {
        myAllModels.addIncludedBuild(DefaultBuild.convertGradleBuild(includedBuild));
      }
      addModels(controller, includedBuild);
    }
    setupIncludedBuildsHierarchy(myAllModels.getIncludedBuilds(), nestedBuilds);
    if (myIsProjectsLoadedAction) {
      controller.getModel(TurnOffDefaultTasks.class);
    }
  }

  private static Set<GradleBuild> getNestedBuilds(@NotNull BuildController controller, @NotNull GradleBuild build) {
    BuildEnvironment environment = controller.getModel(BuildEnvironment.class);
    if (environment == null) {
      return Collections.emptySet();
    }
    GradleVersion gradleVersion = GradleVersion.version(environment.getGradle().getGradleVersion());
    if (gradleVersion.compareTo(GradleVersion.version("3.1")) < 0) {
      return Collections.emptySet();
    }
    Set<String> processedBuildsPaths = new HashSet<>();
    Set<GradleBuild> nestedBuilds = new LinkedHashSet<>();
    String rootBuildPath = build.getBuildIdentifier().getRootDir().getPath();
    processedBuildsPaths.add(rootBuildPath);
    Queue<GradleBuild> queue = new ArrayDeque<>(getEditableBuilds(build, gradleVersion));
    while (!queue.isEmpty()) {
      GradleBuild includedBuild = queue.remove();
      String includedBuildPath = includedBuild.getBuildIdentifier().getRootDir().getPath();
      if (processedBuildsPaths.add(includedBuildPath)) {
        nestedBuilds.add(includedBuild);
        queue.addAll(getEditableBuilds(includedBuild, gradleVersion));
      }
    }
    return nestedBuilds;
  }

  /**
   * Get nested builds to be imported by IDEA
   *
   * @param build parent build
   * @return builds to be imported by IDEA. Before Gradle 8.0 - included builds, 8.0 and later - included and buildSrc builds
   */
  private static DomainObjectSet<? extends GradleBuild> getEditableBuilds(@NotNull GradleBuild build, @NotNull GradleVersion version) {
    if (version.compareTo(GradleVersion.version("8.0")) >= 0) {
      DomainObjectSet<? extends GradleBuild> builds = build.getEditableBuilds();
      if (builds.isEmpty()) {
        return build.getIncludedBuilds();
      }
      else {
        return builds;
      }
    }
    else {
      return build.getIncludedBuilds();
    }
  }

  private static void setupIncludedBuildsHierarchy(List<Build> builds, Set<GradleBuild> gradleBuilds) {
    Set<Build> updatedBuilds = new HashSet<>();
    Map<File, Build> rootDirsToBuilds = new HashMap<>();
    for (Build build : builds) {
      rootDirsToBuilds.put(build.getBuildIdentifier().getRootDir(), build);
    }

    for (GradleBuild gradleBuild : gradleBuilds) {
      Build build = rootDirsToBuilds.get(gradleBuild.getBuildIdentifier().getRootDir());
      if (build == null) {
        continue;
      }

      for (GradleBuild includedGradleBuild : gradleBuild.getIncludedBuilds()) {
        Build buildToUpdate = rootDirsToBuilds.get(includedGradleBuild.getBuildIdentifier().getRootDir());
        if (buildToUpdate instanceof DefaultBuild && updatedBuilds.add(buildToUpdate)) {
          ((DefaultBuild)buildToUpdate).setParentBuildIdentifier(
            new DefaultBuildIdentifier(gradleBuild.getBuildIdentifier().getRootDir()));
        }
      }
    }
  }

  private void addModels(@NotNull BuildController controller, @NotNull GradleBuild gradleBuild) {
    try {
      for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
        addProjectModels(controller, gradleProject);
      }
      addBuildModels(controller, gradleBuild);
    }
    catch (Exception e) {
      // do not fail project import in a preview mode
      if (!myIsPreviewMode) {
        throw new ExternalSystemException(e);
      }
    }
  }

  private void addProjectModels(@NotNull BuildController controller, @NotNull BasicGradleProject gradleProject) {
    for (ProjectImportModelProvider extension : myModelProviders) {
      Set<String> obtainedModels = new HashSet<>();
      long startTime = System.currentTimeMillis();
      extension.populateProjectModels(controller, gradleProject, new ProjectImportModelProvider.ProjectModelConsumer() {
        @Override
        public void consume(@NotNull Object object, @NotNull Class<?> clazz) {
          obtainedModels.add(clazz.getName());
          addProjectModel(gradleProject, object, clazz);
        }
      });
      myAllModels.logPerformance(
        "Ran extension " + extension.getName() +
        " for project " + gradleProject.getProjectIdentifier().getProjectPath() +
        " obtained " + obtainedModels.size() + " model(s): " + joinClassNamesToString(obtainedModels),
        System.currentTimeMillis() - startTime
      );
    }
  }

  private void addBuildModels(@NotNull BuildController controller, @NotNull GradleBuild gradleBuild) {
    for (ProjectImportModelProvider extension : myModelProviders) {
      Set<String> obtainedModels = new HashSet<>();
      long startTime = System.currentTimeMillis();
      extension.populateBuildModels(controller, gradleBuild, new ProjectImportModelProvider.BuildModelConsumer() {
        @Override
        public void consumeProjectModel(@NotNull ProjectModel projectModel, @NotNull Object object, @NotNull Class<?> clazz) {
          obtainedModels.add(clazz.getName());
          addProjectModel(projectModel, object, clazz);
        }

        @Override
        public void consume(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) {
          obtainedModels.add(clazz.getName());
          addBuildModel(buildModel, object, clazz);
        }
      });
      myAllModels.logPerformance(
        "Ran extension " + extension.getName() +
        " for build " + gradleBuild.getBuildIdentifier().getRootDir().getPath() +
        " obtained " + obtainedModels.size() + " model(s): " + joinClassNamesToString(obtainedModels),
        System.currentTimeMillis() - startTime
      );
    }
  }

  private void addProjectModel(@NotNull ProjectModel projectModel, @NotNull Object object, @NotNull Class<?> clazz) {
    myModelConverterExecutor.execute(() -> {
      Object converted = myModelConverter.convert(object);
      myAllModels.addModel(converted, clazz, projectModel);
    });
  }

  private void addBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) {
    myModelConverterExecutor.execute(() -> {
      Object converted = myModelConverter.convert(object);
      myAllModels.addModel(converted, clazz, buildModel);
    });
  }

  @NotNull
  private static String joinClassNamesToString(@NotNull Set<String> names) {
    StringJoiner joiner = new StringJoiner(", ");
    for (String name : names) {
      joiner.add(name);
    }
    return joiner.toString();
  }
}
