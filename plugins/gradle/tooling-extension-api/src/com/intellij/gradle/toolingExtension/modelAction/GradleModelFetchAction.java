// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.tooling.BuildAction;
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
import org.jetbrains.annotations.Nullable;
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
  private final boolean myIsCompositeBuildsSupported;
  private final boolean myIsProjectsLoadedAction;
  private final boolean myParallelModelsFetch;

  public GradleModelFetchAction(
    @NotNull ProjectImportAction.AllModels allModels,
    @NotNull Set<ProjectImportModelProvider> modelProviders,
    @NotNull ModelConverter modelConverter,
    @NotNull ExecutorService modelConverterExecutor,
    boolean isPreviewMode,
    boolean isCompositeBuildsSupported,
    boolean isProjectsLoadedAction,
    boolean parallelModelsFetch
  ) {
    myAllModels = allModels;

    myModelProviders = modelProviders;
    myModelConverter = modelConverter;
    myModelConverterExecutor = modelConverterExecutor;

    myIsPreviewMode = isPreviewMode;
    myIsCompositeBuildsSupported = isCompositeBuildsSupported;
    myIsProjectsLoadedAction = isProjectsLoadedAction;
    myParallelModelsFetch = parallelModelsFetch;
  }

  public void execute(@NotNull DefaultBuildController controller) {
    GradleBuild mainGradleBuild = controller.getBuildModel();

    //We only need these later, but need to fetch them before fetching other models because of https://github.com/gradle/gradle/issues/20008
    Set<GradleBuild> nestedBuilds = getNestedBuilds(controller, mainGradleBuild);

    addProjectModels(controller, mainGradleBuild);
    addBuildModels(controller, mainGradleBuild);

    for (GradleBuild includedBuild : nestedBuilds) {
      if (!myIsProjectsLoadedAction) {
        myAllModels.addIncludedBuild(DefaultBuild.convertGradleBuild(includedBuild));
      }
      addProjectModels(controller, includedBuild);
      addBuildModels(controller, includedBuild);
    }
    setupIncludedBuildsHierarchy(myAllModels.getIncludedBuilds(), nestedBuilds);
    if (myIsProjectsLoadedAction) {
      controller.getModel(TurnOffDefaultTasks.class);
    }
  }

  private Set<GradleBuild> getNestedBuilds(@NotNull BuildController controller, @NotNull GradleBuild build) {
    BuildEnvironment environment = controller.getModel(BuildEnvironment.class);
    GradleVersion envGradleVersion = null;
    if (environment != null) {
      // call to GradleVersion.current() will load version class from client classloader and return TAPI version number
      envGradleVersion = GradleVersion.version(environment.getGradle().getGradleVersion());
    }
    if (!myIsCompositeBuildsSupported) {
      return Collections.emptySet();
    }
    Set<String> processedBuildsPaths = new HashSet<>();
    Set<GradleBuild> nestedBuilds = new LinkedHashSet<>();
    String rootBuildPath = build.getBuildIdentifier().getRootDir().getPath();
    processedBuildsPaths.add(rootBuildPath);
    Queue<GradleBuild> queue = new ArrayDeque<>(getEditableBuilds(build, envGradleVersion));
    while (!queue.isEmpty()) {
      GradleBuild includedBuild = queue.remove();
      String includedBuildPath = includedBuild.getBuildIdentifier().getRootDir().getPath();
      if (processedBuildsPaths.add(includedBuildPath)) {
        nestedBuilds.add(includedBuild);
        queue.addAll(getEditableBuilds(includedBuild, envGradleVersion));
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
  private static DomainObjectSet<? extends GradleBuild> getEditableBuilds(@NotNull GradleBuild build, @Nullable GradleVersion version) {
    if (version != null && version.compareTo(GradleVersion.version("8.0")) >= 0) {
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

  private void addProjectModels(@NotNull BuildController controller, @NotNull GradleBuild build) {
    if (myParallelModelsFetch) {
      // Prepare nested build actions.
      List<BuildAction<?>> buildActions = new ArrayList<>();
      for (BasicGradleProject gradleProject : build.getProjects()) {
        buildActions.add(new BuildAction<Object>() {
          @Override
          public Object execute(BuildController controller) {
            addProjectModels(controller, gradleProject);
            return null;
          }
        });
      }
      controller.run(buildActions);
    }
    else {
      for (BasicGradleProject gradleProject : build.getProjects()) {
        addProjectModels(controller, gradleProject);
      }
    }
  }

  /**
   * Gets project level models for a given {@code project} and returns a collection of actions,
   * which when executed add these models to {@code allModels}.
   *
   * <p>The actions returned by this method are supposed to be executed on a single thread.
   */
  private void addProjectModels(@NotNull BuildController controller, @NotNull final BasicGradleProject gradleProject) {
    try {
      for (ProjectImportModelProvider extension : myModelProviders) {
        final Set<String> obtainedModels = new HashSet<>();
        long startTime = System.currentTimeMillis();
        extension.populateProjectModels(controller, gradleProject, new ProjectImportModelProvider.ProjectModelConsumer() {
          @Override
          public void consume(@NotNull Object object, @NotNull Class clazz) {
            obtainedModels.add(clazz.getName());
            addProjectModel(gradleProject, object, clazz);
          }
        });
        myAllModels.logPerformance(
          "Ran extension " + extension.getClass().getName() +
          " for project " + gradleProject.getProjectIdentifier().getProjectPath() +
          " obtained " + obtainedModels.size() + " model(s): " + joinClassNamesToString(obtainedModels),
          System.currentTimeMillis() - startTime);
      }
    }
    catch (Exception e) {
      // do not fail project import in a preview mode
      if (!myIsPreviewMode) {
        throw new ExternalSystemException(e);
      }
    }
  }

  private void addBuildModels(@NotNull BuildController controller, @NotNull GradleBuild gradleBuild) {
    try {
      for (ProjectImportModelProvider extension : myModelProviders) {
        final Set<String> obtainedModels = new HashSet<>();
        long startTime = System.currentTimeMillis();
        extension.populateBuildModels(controller, gradleBuild, new ProjectImportModelProvider.BuildModelConsumer() {
          @Override
          public void consumeProjectModel(@NotNull ProjectModel projectModel, @NotNull Object object, @NotNull Class clazz) {
            obtainedModels.add(clazz.getName());
            addProjectModel(projectModel, object, clazz);
          }

          @Override
          public void consume(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class clazz) {
            obtainedModels.add(clazz.getName());
            addBuildModel(buildModel, object, clazz);
          }
        });
        myAllModels.logPerformance(
          "Ran extension " +
          extension.getClass().getName() +
          " for build " + gradleBuild.getBuildIdentifier().getRootDir().getPath() +
          " obtained " + obtainedModels.size() + " model(s): " + joinClassNamesToString(obtainedModels),
          System.currentTimeMillis() - startTime);
      }
    }
    catch (Exception e) {
      // do not fail project import in a preview mode
      if (!myIsPreviewMode) {
        throw new ExternalSystemException(e);
      }
    }
  }

  private void addProjectModel(final @NotNull ProjectModel projectModel, final @NotNull Object object, final @NotNull Class<?> clazz) {
    Runnable convert = new Runnable() {
      @Override
      public void run() {
        Object converted = myModelConverter.convert(object);
        myAllModels.addModel(converted, clazz, projectModel);
      }
    };
    myModelConverterExecutor.execute(convert);
  }

  private void addBuildModel(final @NotNull BuildModel buildModel, final @NotNull Object object, final @NotNull Class<?> clazz) {
    Runnable convert = new Runnable() {
      @Override
      public void run() {
        Object converted = myModelConverter.convert(object);
        myAllModels.addModel(converted, clazz, buildModel);
      }
    };
    myModelConverterExecutor.execute(convert);
  }

  @NotNull
  private static String joinClassNamesToString(@NotNull Set<String> names) {
    StringBuilder sb = new StringBuilder();
    for (Iterator<String> it = names.iterator(); it.hasNext(); ) {
      sb.append(it.next());
      if (it.hasNext()) {
        sb.append(", ");
      }
    }

    return sb.toString();
  }
}
