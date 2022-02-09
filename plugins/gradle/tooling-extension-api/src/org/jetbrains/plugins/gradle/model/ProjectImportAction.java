// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtilRt;
import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.JavaEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.BuildModelConsumer;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.ProjectModelConsumer;
import org.jetbrains.plugins.gradle.model.internal.TurnOffDefaultTasks;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalJavaEnvironment;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.build.InternalBuildEnvironment;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Vladislav.Soroka
 */
public class ProjectImportAction implements BuildAction<ProjectImportAction.AllModels>, Serializable {
  private static final ModelConverter NOOP_CONVERTER = new NoopConverter();
  public static final String IDEA_BACKGROUND_CONVERT = "idea.background.convert";

  private final Set<ProjectImportModelProvider> myProjectsLoadedModelProviders = new HashSet<ProjectImportModelProvider>();
  private final Set<ProjectImportModelProvider> myBuildFinishedModelProviders = new HashSet<ProjectImportModelProvider>();
  private final Set<Class<?>> myTargetTypes = new HashSet<Class<?>>();
  private final boolean myIsPreviewMode;
  private final boolean myIsCompositeBuildsSupported;
  private boolean myUseProjectsLoadedPhase;
  private boolean myParallelModelsFetch;
  private AllModels myAllModels = null;
  @Nullable
  private transient GradleBuild myGradleBuild;
  private transient ExecutorService myConverterExecutor;
  private ModelConverter myModelConverter;

  public ProjectImportAction(boolean isPreviewMode, boolean isCompositeBuildsSupported) {
    myIsPreviewMode = isPreviewMode;
    myIsCompositeBuildsSupported = isCompositeBuildsSupported;
  }

  public void addProjectImportModelProvider(@NotNull ProjectImportModelProvider provider) {
    addProjectImportModelProvider(provider, false);
  }

  public void addProjectImportModelProvider(@NotNull ProjectImportModelProvider provider, boolean isProjectLoadedProvider) {
    if (isProjectLoadedProvider) {
      myProjectsLoadedModelProviders.add(provider);
    }
    else {
      myBuildFinishedModelProviders.add(provider);
    }
  }

  @ApiStatus.Internal
  public Set<Class<?>> getModelProvidersClasses() {
    Set<Class<?>> result = new LinkedHashSet<Class<?>>();
    for (ProjectImportModelProvider provider : myProjectsLoadedModelProviders) {
      result.add(provider.getClass());
    }
    for (ProjectImportModelProvider provider : myBuildFinishedModelProviders) {
      result.add(provider.getClass());
    }
    return result;
  }

  public void addTargetTypes(@NotNull Set<Class<?>> targetTypes) {
    myTargetTypes.addAll(targetTypes);
  }

  public void prepareForPhasedExecuter() {
    myUseProjectsLoadedPhase = true;
  }

  public void prepareForNonPhasedExecuter() {
    myUseProjectsLoadedPhase = false;
  }

  @NotNull
  protected ModelConverter getToolingModelConverter(@NotNull BuildController controller) {
    return NOOP_CONVERTER;
  }

  @Nullable
  @Override
  public AllModels execute(final BuildController controller) {
    if (!System.getProperties().containsKey(IDEA_BACKGROUND_CONVERT) || Boolean.getBoolean(IDEA_BACKGROUND_CONVERT)) {
      myConverterExecutor =  Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@NotNull Runnable runnable) {
          return new Thread(runnable, "idea-tooling-model-converter");
        }
      });
    }
    configureAdditionalTypes(controller);
    final boolean isProjectsLoadedAction = myAllModels == null && myUseProjectsLoadedPhase;
    if (isProjectsLoadedAction || !myUseProjectsLoadedPhase) {
      long startTime = System.currentTimeMillis();
      myGradleBuild = controller.getBuildModel();
      Build mainBuild = convert(myGradleBuild);
      AllModels allModels = new AllModels(mainBuild);
      allModels.logPerformance("Get model GradleBuild", System.currentTimeMillis() - startTime);
      long startTimeBuildEnv = System.currentTimeMillis();
      BuildEnvironment buildEnvironment = controller.findModel(BuildEnvironment.class);
      allModels.setBuildEnvironment(convert(buildEnvironment));
      allModels.logPerformance("Get model BuildEnvironment", System.currentTimeMillis() - startTimeBuildEnv);
      myAllModels = allModels;
      myModelConverter = getToolingModelConverter(controller);
    }

    assert myGradleBuild != null;
    assert myModelConverter != null;
    final MyBuildController wrappedController = new MyBuildController(controller, myGradleBuild);
    fetchProjectBuildModels(wrappedController, isProjectsLoadedAction, myGradleBuild);
    addBuildModels(wrappedController, myAllModels, myGradleBuild, isProjectsLoadedAction);

    if (myIsCompositeBuildsSupported) {
      forEachNestedBuild(myGradleBuild, new GradleBuildConsumer() {
        @Override
        public void accept(@NotNull GradleBuild includedBuild) {
          if (!isProjectsLoadedAction) {
            myAllModels.getIncludedBuilds().add(convert(includedBuild));
          }
          fetchProjectBuildModels(wrappedController, isProjectsLoadedAction, includedBuild);
          addBuildModels(wrappedController, myAllModels, includedBuild, isProjectsLoadedAction);
        }
      });
    }
    if (isProjectsLoadedAction) {
      wrappedController.getModel(TurnOffDefaultTasks.class);
    }
    if (myConverterExecutor != null) {
      myConverterExecutor.shutdown();
      try {
        myConverterExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return isProjectsLoadedAction && !myAllModels.hasModels() ? null : myAllModels;
  }

  @ApiStatus.Internal
  public void setParallelModelsFetch(boolean parallelModelsFetch) {
    myParallelModelsFetch = parallelModelsFetch;
  }

  @Contract("null -> null")
  private static BuildEnvironment convert(final @Nullable BuildEnvironment buildEnvironment) {
    if (buildEnvironment == null) return null;
    return buildEnvironment instanceof InternalBuildEnvironment ? buildEnvironment :
           new InternalBuildEnvironment(
             new Supplier<InternalBuildIdentifier>() {
               @Override
               public InternalBuildIdentifier get() {
                 return new InternalBuildIdentifier(buildEnvironment.getBuildIdentifier().getRootDir());
               }
             },
             new Supplier<InternalJavaEnvironment>() {
               @Override
               public InternalJavaEnvironment get() {
                 JavaEnvironment java = buildEnvironment.getJava();
                 return new InternalJavaEnvironment(java.getJavaHome(), java.getJvmArguments());
               }
             },
             new Supplier<File>() {
               @Override
               public File get() {
                 return buildEnvironment.getGradle().getGradleUserHome();
               }
             },
             buildEnvironment.getGradle().getGradleVersion());
  }

  private interface GradleBuildConsumer {
    void accept(@NotNull GradleBuild build);
  }

  private static void forEachNestedBuild(@NotNull GradleBuild rootBuild, @NotNull GradleBuildConsumer buildConsumer) {
    Set<String> processedBuildsPaths = new HashSet<String>();
    String rootBuildPath = rootBuild.getBuildIdentifier().getRootDir().getPath();
    processedBuildsPaths.add(rootBuildPath);
    Queue<GradleBuild> queue = new LinkedList<GradleBuild>(rootBuild.getIncludedBuilds());
    while (!queue.isEmpty()) {
      GradleBuild includedBuild = queue.remove();
      String includedBuildPath = includedBuild.getBuildIdentifier().getRootDir().getPath();
      if (processedBuildsPaths.add(includedBuildPath)) {
        buildConsumer.accept(includedBuild);
        queue.addAll(includedBuild.getIncludedBuilds());
      }
    }
  }

  private void fetchProjectBuildModels(BuildController controller, final boolean isProjectsLoadedAction, GradleBuild build) {
    // Prepare nested build actions.
    List<BuildAction<List<Runnable>>> buildActions = new ArrayList<BuildAction<List<Runnable>>>();
    for (final BasicGradleProject gradleProject : build.getProjects()) {
      buildActions.add(
        new BuildAction<List<Runnable>>() {
          @Override
          public List<Runnable> execute(BuildController controller) {
            return getProjectModels(controller, myAllModels, gradleProject, isProjectsLoadedAction);
          }
        }
      );
    }

    // Execute nested build actions.
    List<List<Runnable>> addFetchedModelActions = new ArrayList<List<Runnable>>(buildActions.size());
    if (myParallelModelsFetch) {
      addFetchedModelActions.addAll(controller.run(buildActions));
    }
    else {
      for (BuildAction<List<Runnable>> buildAction : buildActions) {
        addFetchedModelActions.add(buildAction.execute(controller));
      }
    }

    // Execute returned actions sequentially in one thread to populate myAllModels.
    for (List<Runnable> actions : addFetchedModelActions) {
      for (Runnable action : actions) {
        action.run();
      }
    }
  }

  @NotNull
  private static Build convert(@NotNull GradleBuild build) {
    DefaultBuild rootProject = new DefaultBuild(build.getRootProject().getName(), build.getBuildIdentifier().getRootDir());
    for (BasicGradleProject project : build.getProjects()) {
      rootProject.addProject(project.getName(), project.getProjectIdentifier());
    }
    return rootProject;
  }

  private void configureAdditionalTypes(BuildController controller) {
    if (myTargetTypes.isEmpty()) return;
    try {
      ProtocolToModelAdapter modelAdapter =
        ReflectionUtilRt.getField(controller.getClass(), controller, ProtocolToModelAdapter.class, "adapter");
      if (modelAdapter == null) return;
      TargetTypeProvider typeProvider =
        ReflectionUtilRt.getField(ProtocolToModelAdapter.class, modelAdapter, TargetTypeProvider.class, "targetTypeProvider");
      if (typeProvider == null) return;
      //noinspection unchecked
      Map<String, Class<?>> targetTypes =
        ReflectionUtilRt.getField(typeProvider.getClass(), typeProvider, Map.class, "configuredTargetTypes");
      if (targetTypes == null) return;
      for (Class<?> targetType : myTargetTypes) {
        targetTypes.put(targetType.getCanonicalName(), targetType);
      }
    }
    catch (Exception ignore) {
    }
  }

  /**
   * Gets project level models for a given {@code project} and returns a collection of actions,
   * which when executed add these models to {@code allModels}.
   *
   * <p>The actions returned by this method are supposed to be executed on a single thread.
   */
  private List<Runnable> getProjectModels(@NotNull BuildController controller,
                                          @NotNull final AllModels allModels,
                                          @NotNull final BasicGradleProject project,
                                          boolean isProjectsLoadedAction) {
    try {
      final List<Runnable> result = new ArrayList<Runnable>();
      Set<ProjectImportModelProvider> modelProviders = getModelProviders(isProjectsLoadedAction);
      for (ProjectImportModelProvider extension : modelProviders) {
        final Set<String> obtainedModels = new HashSet<String>();
        long startTime = System.currentTimeMillis();
        ProjectModelConsumer modelConsumer = new ProjectModelConsumer() {
          @Override
          public void consume(final @NotNull Object object, final @NotNull Class clazz) {
            Runnable convert = new Runnable() {
              @Override
              public void run() {
                Object o = myModelConverter.convert(object);
                allModels.addModel(o, clazz, project);
              }
            };
            if (myConverterExecutor != null) {
              myConverterExecutor.execute(convert);
            } else {
              result.add(convert);
            }
            obtainedModels.add(clazz.getName());
          }
        };
        extension.populateProjectModels(controller, project, modelConsumer);
        allModels.logPerformance(
          "Ran extension " + extension.getClass().getName() +
          " for project " + project.getProjectIdentifier().getProjectPath() +
          " obtained " + obtainedModels.size() + " model(s): " + joinClassNamesToString(obtainedModels),
          System.currentTimeMillis() - startTime);
      }
      return result;
    }
    catch (Exception e) {
      // do not fail project import in a preview mode
      if (!myIsPreviewMode) {
        throw new ExternalSystemException(e);
      }
    }
    return Collections.emptyList();
  }

  private void addBuildModels(@NotNull BuildController controller,
                              @NotNull final AllModels allModels,
                              @NotNull final GradleBuild buildModel,
                              boolean isProjectsLoadedAction) {
    try {
      Set<ProjectImportModelProvider> modelProviders = getModelProviders(isProjectsLoadedAction);
      for (ProjectImportModelProvider extension : modelProviders) {
        final Set<String> obtainedModels = new HashSet<String>();
        long startTime = System.currentTimeMillis();
        BuildModelConsumer modelConsumer = new BuildModelConsumer() {
          @Override
          public void consumeProjectModel(@NotNull final ProjectModel projectModel, @NotNull final Object object, @NotNull final Class clazz) {
            obtainedModels.add(clazz.getName());
            Runnable convert = new Runnable() {
              @Override
              public void run() {
                Object converted = myModelConverter.convert(object);
                allModels.addModel(converted, clazz, projectModel);
              }
            };
            if (myConverterExecutor != null) {
              myConverterExecutor.execute(convert);
            } else {
              convert.run();
            }
          }

          @Override
          public void consume(@NotNull final BuildModel buildModel, @NotNull final Object object, @NotNull final Class clazz) {
            obtainedModels.add(clazz.getName());
            Runnable convert = new Runnable() {
              @Override
              public void run() {
                Object converted = myModelConverter.convert(object);
                allModels.addModel(converted, clazz, buildModel);
              }
            };
            if (myConverterExecutor != null) {
              myConverterExecutor.execute(convert);
            } else {
              convert.run();
            }
          }
        };
        extension.populateBuildModels(controller, buildModel, modelConsumer);
        allModels.logPerformance(
          "Ran extension " +
          extension.getClass().getName() +
          " for build " + buildModel.getBuildIdentifier().getRootDir().getPath() +
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

  private Set<ProjectImportModelProvider> getModelProviders(boolean isProjectsLoadedAction) {
    Set<ProjectImportModelProvider> modelProviders = new LinkedHashSet<ProjectImportModelProvider>();
    if (!myUseProjectsLoadedPhase) {
      modelProviders.addAll(myProjectsLoadedModelProviders);
      modelProviders.addAll(myBuildFinishedModelProviders);
    }
    else {
      modelProviders = isProjectsLoadedAction ? myProjectsLoadedModelProviders : myBuildFinishedModelProviders;
    }
    return modelProviders;
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

  @ApiStatus.Experimental
  public interface ModelConverter extends Serializable {
    Object convert(Object object);
  }

  // Note: This class is NOT thread safe and it is supposed to be used from a single thread.
  //       Performance logging related methods are thread safe.
  public static final class AllModels extends ModelsHolder<BuildModel, ProjectModel> {
    @NotNull private final List<Build> includedBuilds = new ArrayList<Build>();
    private final Map<String, Long> performanceTrace = new ConcurrentHashMap<String, Long>();
    private transient Map<String, String> myBuildsKeyPrefixesMapping;

    public AllModels(@NotNull Build mainBuild) {
      super(mainBuild);
    }

    public AllModels(@NotNull IdeaProject ideaProject) {
      super(new LegacyIdeaProjectModelAdapter(ideaProject));
      addModel(ideaProject, IdeaProject.class);
    }

    /**
     * @deprecated use {@link #getModel(Class<IdeaProject>)}
     */
    @NotNull
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public IdeaProject getIdeaProject() {
      IdeaProject ideaProject = getModel(IdeaProject.class);
      assert ideaProject != null;
      return ideaProject;
    }

    @NotNull
    public Build getMainBuild() {
      return (Build)getRootModel();
    }

    @NotNull
    public List<Build> getIncludedBuilds() {
      return includedBuilds;
    }

    @Nullable
    public BuildEnvironment getBuildEnvironment() {
      return getModel(BuildEnvironment.class);
    }

    public void setBuildEnvironment(@Nullable BuildEnvironment buildEnvironment) {
      if (buildEnvironment != null) {
        addModel(buildEnvironment, BuildEnvironment.class);
      }
    }

    public void logPerformance(@NotNull final String description, long millis) {
      performanceTrace.put(description, millis);
    }

    public Map<String, Long> getPerformanceTrace() {
      return performanceTrace;
    }

    @Override
    public void applyPathsConverter(@NotNull Consumer<Object> pathsConverter) {
      super.applyPathsConverter(pathsConverter);
      BuildEnvironment buildEnvironment = getBuildEnvironment();
      if (buildEnvironment != null) {
        pathsConverter.consume(buildEnvironment);
      }
      myBuildsKeyPrefixesMapping = new HashMap<String, String>();
      convertPaths(pathsConverter, getMainBuild());
      for (Build includedBuild : includedBuilds) {
        convertPaths(pathsConverter, includedBuild);
      }
    }

    private void convertPaths(@NotNull Consumer<Object> fileMapper, @NotNull Build build) {
      String originalKey = getBuildKeyPrefix(build.getBuildIdentifier());
      fileMapper.consume(build);
      String currentKey = getBuildKeyPrefix(build.getBuildIdentifier());
      if (!originalKey.equals(currentKey)) {
        myBuildsKeyPrefixesMapping.put(currentKey, originalKey);
      }
    }

    @NotNull
    @Override
    protected String getBuildKeyPrefix(@NotNull BuildIdentifier buildIdentifier) {
      String currentKey = super.getBuildKeyPrefix(buildIdentifier);
      String originalKey = myBuildsKeyPrefixesMapping == null ? null : myBuildsKeyPrefixesMapping.get(currentKey);
      return originalKey == null ? currentKey : originalKey;
    }
  }

  private final static class DefaultBuild implements Build, Serializable {
    private final String myName;
    private final DefaultBuildIdentifier myBuildIdentifier;
    private final Collection<Project> myProjects = new ArrayList<Project>(0);

    private DefaultBuild(String name, File rootDir) {
      myName = name;
      myBuildIdentifier = new DefaultBuildIdentifier(rootDir);
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
      return myBuildIdentifier;
    }

    @Override
    public Collection<Project> getProjects() {
      return myProjects;
    }

    private void addProject(String name, final ProjectIdentifier projectIdentifier) {
      final String projectPath = projectIdentifier.getProjectPath();
      File rootDir = myBuildIdentifier.getRootDir();
      assert rootDir.getPath().equals(projectIdentifier.getBuildIdentifier().getRootDir().getPath());
      myProjects.add(new DefaultProjectModel(name, rootDir, projectPath));
    }

    private final static class DefaultProjectModel implements Project, Serializable {
      private final String myName;
      private final DefaultProjectIdentifier myProjectIdentifier;

      private DefaultProjectModel(@NotNull String name, @NotNull File rootDir, @NotNull String projectPath) {
        myName = name;
        myProjectIdentifier = new DefaultProjectIdentifier(rootDir, projectPath);
      }

      @Override
      public String getName() {
        return myName;
      }

      @Override
      public ProjectIdentifier getProjectIdentifier() {
        return myProjectIdentifier;
      }

      @Override
      public String toString() {
        return "ProjectModel{" +
               "name='" + myName + '\'' +
               ", id=" + myProjectIdentifier +
               '}';
      }
    }
  }

  private final static class MyBuildController implements BuildController {
    private final BuildController myDelegate;
    private final GradleBuild myMainGradleBuild;
    private final Model myMyMainGradleBuildRootProject;

    private MyBuildController(@NotNull BuildController buildController, @NotNull GradleBuild mainGradleBuild) {
      myDelegate = buildController;
      myMainGradleBuild = mainGradleBuild;
      myMyMainGradleBuildRootProject = myMainGradleBuild.getRootProject();
    }

    @Override
    public <T> T getModel(Class<T> aClass) throws UnknownModelException {
      if (aClass == GradleBuild.class) {
        //noinspection unchecked
        return (T)myMainGradleBuild;
      }
      return myDelegate.getModel(myMyMainGradleBuildRootProject, aClass);
    }

    @Override
    public <T> T findModel(Class<T> aClass) {
      if (aClass == GradleBuild.class) {
        //noinspection unchecked
        return (T)myMainGradleBuild;
      }
      return myDelegate.findModel(myMyMainGradleBuildRootProject, aClass);
    }

    @Override
    public GradleBuild getBuildModel() {
      return myMainGradleBuild;
    }

    @Override
    public <T> T getModel(Model model, Class<T> aClass) throws UnknownModelException {
      if (isMainBuild(model)) {
        return getModel(aClass);
      }
      else {
        return myDelegate.getModel(model, aClass);
      }
    }

    @Override
    public <T> T findModel(Model model, Class<T> aClass) {
      if (isMainBuild(model)) {
        return findModel(aClass);
      }
      else {
        return myDelegate.findModel(model, aClass);
      }
    }

    @Override
    public <T, P> T getModel(Class<T> aClass, Class<P> aClass1, Action<? super P> action)
      throws UnsupportedVersionException {
      return myDelegate.getModel(myMyMainGradleBuildRootProject, aClass, aClass1, action);
    }

    @Override
    public <T, P> T findModel(Class<T> aClass, Class<P> aClass1, Action<? super P> action) {
      return myDelegate.findModel(myMyMainGradleBuildRootProject, aClass, aClass1, action);
    }

    @Override
    public <T, P> T getModel(Model model, Class<T> aClass, Class<P> aClass1, Action<? super P> action)
      throws UnsupportedVersionException {
      if (isMainBuild(model)) {
        return getModel(aClass, aClass1, action);
      }
      else {
        return myDelegate.getModel(model, aClass, aClass1, action);
      }
    }

    @Override
    public <T, P> T findModel(Model model, Class<T> aClass, Class<P> aClass1, Action<? super P> action) {
      if (isMainBuild(model)) {
        return findModel(aClass, aClass1, action);
      }
      else {
        return myDelegate.findModel(model, aClass, aClass1, action);
      }
    }

    @Override
    public <T> List<T> run(Collection<? extends BuildAction<? extends T>> collection) {
      return myDelegate.run(collection);
    }

    @Override
    public boolean getCanQueryProjectModelInParallel(Class<?> aClass) {
      return myDelegate.getCanQueryProjectModelInParallel(aClass);
    }

    private boolean isMainBuild(Model model) {
      return model == null || model == myMainGradleBuild;
    }
  }

  private static class NoopConverter implements ModelConverter {
    @Override
    public Object convert(Object object) {
      return object;
    }
  }
}
