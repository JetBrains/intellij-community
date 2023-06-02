// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.execution.CommandLineUtil;
import com.intellij.externalSystem.JavaModuleData;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleSdkData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectSdkData;
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision;
import com.intellij.openapi.roots.ui.configuration.SdkLookupUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.gradle.api.JavaVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.AnnotationProcessingData;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public class JavaGradleProjectResolver extends AbstractProjectResolverExtension {
  private final static Logger LOG = Logger.getInstance(JavaGradleProjectResolver.class);

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    populateJavaProjectCompilerSettings(gradleProject, ideProject);
    populateProjectSdkModel(gradleProject, ideProject);
    nextResolver.populateProjectExtraModels(gradleProject, ideProject);
  }

  @NotNull
  private String getCompileOutputPath() {
    String projectDirPath = resolverCtx.getProjectPath();
    // Gradle API doesn't expose gradleProject compile output path yet.
    return projectDirPath + "/build/classes";
  }

  @Override
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    populateJavaModuleCompilerSettings(gradleModule, ideModule);
    populateBuildScriptClasspathData(gradleModule, ideModule);
    populateAnnotationProcessorData(gradleModule, ideModule);
    populateDependenciesGraphData(gradleModule, ideModule);
    nextResolver.populateModuleExtraModels(gradleModule, ideModule);
  }

  private void populateAnnotationProcessorData(@NotNull IdeaModule gradleModule,
                                               @NotNull DataNode<ModuleData> ideModule) {
    final AnnotationProcessingModel apModel = resolverCtx.getExtraProject(gradleModule, AnnotationProcessingModel.class);
    if (apModel == null) {
      return;
    }
    if (!resolverCtx.isResolveModulePerSourceSet()) {
      final AnnotationProcessingData apData = getMergedAnnotationProcessingData(apModel);
      DataNode<AnnotationProcessingData> dataNode = ideModule.createChild(AnnotationProcessingData.KEY, apData);
      populateAnnotationProcessingOutput(dataNode, apModel);
    } else {
      Collection<DataNode<GradleSourceSetData>> all = ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY);
      for (DataNode<GradleSourceSetData> node : all) {
        final AnnotationProcessingData apData = getAnnotationProcessingData(apModel, node.getData().getModuleName());
        if (apData != null) {
          DataNode<AnnotationProcessingData> dataNode = node.createChild(AnnotationProcessingData.KEY, apData);
          populateAnnotationProcessorOutput(dataNode, apModel, node.getData().getModuleName());
        }
      }
    }
  }

  private static void populateAnnotationProcessorOutput(@NotNull DataNode<AnnotationProcessingData> parent,
                                                        @NotNull AnnotationProcessingModel apModel,
                                                        @NotNull String sourceSetName) {
    AnnotationProcessingConfig config = apModel.bySourceSetName(sourceSetName);
    if (config != null && config.getProcessorOutput() != null) {
      parent.createChild(AnnotationProcessingData.OUTPUT_KEY,
                         new AnnotationProcessingData.AnnotationProcessorOutput(config.getProcessorOutput(), config.isTestSources()));
    }
  }

  private static void populateAnnotationProcessingOutput(@NotNull DataNode<AnnotationProcessingData> parent,
                                                         @NotNull AnnotationProcessingModel apModel) {
    for (AnnotationProcessingConfig config : apModel.allConfigs().values()) {
      if (config.getProcessorOutput() != null) {
        parent.createChild(AnnotationProcessingData.OUTPUT_KEY,
                           new AnnotationProcessingData.AnnotationProcessorOutput(config.getProcessorOutput(), config.isTestSources()));
      }
    }
  }

  @NotNull
  private static AnnotationProcessingData getMergedAnnotationProcessingData(@NotNull AnnotationProcessingModel apModel) {

    final Set<String> mergedAnnotationProcessorPath = new LinkedHashSet<>();
    for (AnnotationProcessingConfig config : apModel.allConfigs().values()) {
      mergedAnnotationProcessorPath.addAll(config.getAnnotationProcessorPath());
    }

    final List<String> apArguments = new ArrayList<>();
    final AnnotationProcessingConfig mainConfig = apModel.bySourceSetName("main");
    if (mainConfig != null) {
       apArguments.addAll(mainConfig.getAnnotationProcessorArguments());
    }

    return AnnotationProcessingData.create(mergedAnnotationProcessorPath, apArguments);
  }

  @Nullable
  private static AnnotationProcessingData getAnnotationProcessingData(@NotNull AnnotationProcessingModel apModel,
                                                                      @NotNull String sourceSetName) {
    AnnotationProcessingConfig config = apModel.bySourceSetName(sourceSetName);
    if (config == null) {
      return null;
    } else {
      return AnnotationProcessingData.create(config.getAnnotationProcessorPath(),
                                             config.getAnnotationProcessorArguments());
    }
  }

  private void populateBuildScriptClasspathData(@NotNull IdeaModule gradleModule,
                                                @NotNull DataNode<ModuleData> ideModule) {
    final BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);
    final List<BuildScriptClasspathData.ClasspathEntry> classpathEntries;
    if (buildScriptClasspathModel != null) {
      classpathEntries = ContainerUtil.map(
        buildScriptClasspathModel.getClasspath(),
        (Function<ClasspathEntryModel, BuildScriptClasspathData.ClasspathEntry>)model -> BuildScriptClasspathData.ClasspathEntry
          .create(model.getClasses(), model.getSources(), model.getJavadoc()));
    }
    else {
      classpathEntries = ContainerUtil.emptyList();
    }
    BuildScriptClasspathData buildScriptClasspathData = new BuildScriptClasspathData(GradleConstants.SYSTEM_ID, classpathEntries);
    buildScriptClasspathData.setGradleHomeDir(buildScriptClasspathModel != null ? buildScriptClasspathModel.getGradleHomeDir() : null);
    ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);
  }

  private void populateDependenciesGraphData(@NotNull IdeaModule gradleModule,
                                             @NotNull DataNode<ModuleData> ideModule) {
    final ProjectDependencies projectDependencies = resolverCtx.getExtraProject(gradleModule, ProjectDependencies.class);
    if (projectDependencies != null) {
      ideModule.createChild(ProjectKeys.DEPENDENCIES_GRAPH, projectDependencies);
    }
  }

  @Override
  public void enhanceTaskProcessing(@NotNull List<String> taskNames,
                                    @NotNull Consumer<String> initScriptConsumer,
                                    @NotNull Map<String, String> parameters) {

    var isRunAsTest = Boolean.parseBoolean(parameters.get(IS_RUN_AS_TEST_KEY));
    var isBuiltInTestEventsUsed = Boolean.parseBoolean(parameters.get(IS_BUILT_IN_TEST_EVENTS_USED_KEY));
    var jvmParametersSetup = parameters.get(GradleProjectResolverExtension.JVM_PARAMETERS_SETUP_KEY);
    if (isRunAsTest) {
      var initScript = isBuiltInTestEventsUsed
                       ? GradleInitScriptUtil.loadFileComparisonTestLoggerInitScript()
                       : GradleInitScriptUtil.loadIjTestLoggerInitScript();
      initScriptConsumer.consume(initScript);
    }
    enhanceTaskProcessing(taskNames, jvmParametersSetup, initScriptConsumer);
  }

  @Override
  public void enhanceTaskProcessing(@NotNull List<String> taskNames,
                                    @Nullable String jvmParametersSetup,
                                    @NotNull Consumer<String> initScriptConsumer) {
    if (!StringUtil.isEmpty(jvmParametersSetup)) {
      LOG.assertTrue(!jvmParametersSetup.contains(ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX),
                     "Please use org.jetbrains.plugins.gradle.service.debugger.GradleJvmDebuggerBackend to setup debugger");

      final String names = "[" + toStringListLiteral(taskNames, ", ") + "]";
      List<String> argv = ParametersListUtil.parse(jvmParametersSetup);
      if (SystemInfo.isWindows) {
        argv = ContainerUtil.map(argv, s -> CommandLineUtil.escapeParameterOnWindows(s, false));
      }
      final String jvmArgs = toStringListLiteral(argv, " << ");

      final String[] lines = {
        "gradle.taskGraph.whenReady { taskGraph ->",
        "  taskGraph.allTasks.each { Task task ->",
        "    if (task instanceof JavaForkOptions && (" + names + ".contains(task.name) || " + names + ".contains(task.path))) {",
        "        def jvmArgs = task.jvmArgs.findAll{!it?.startsWith('-agentlib:jdwp') && !it?.startsWith('-Xrunjdwp')}",
        "        jvmArgs << " + jvmArgs,
        "        task.jvmArgs = jvmArgs",
        "    }",
        "  }",
        "}",
      };
      final String script = StringUtil.join(lines, System.lineSeparator());
      initScriptConsumer.consume(script);
    }
  }

  @NotNull
  private static String toStringListLiteral(@NotNull List<String> strings, @NotNull String separator) {
    final List<String> quotedStrings = ContainerUtil.map(strings, s -> StringUtil.escapeChar(toStringLiteral(s), '$'));
    return StringUtil.join(quotedStrings, separator);
  }

  @NotNull
  private static String toStringLiteral(@NotNull String s) {
    return StringUtil.wrapWithDoubleQuote(StringUtil.escapeStringCharacters(s));
  }

  @NotNull
  @Override
  public Set<Class<?>> getExtraProjectModelClasses() {
    return Set.of(AnnotationProcessingModel.class, ProjectDependencies.class);
  }

  private void populateJavaProjectCompilerSettings(@NotNull IdeaProject ideaProject, @NotNull DataNode<ProjectData> projectNode) {
    String compileOutputPath = getCompileOutputPath();

    LanguageLevel languageLevel = getLanguageLevel(ideaProject);
    String targetBytecodeVersion = getTargetBytecodeVersion(ideaProject);

    JavaSdkVersion jdkVersion = JavaProjectData.resolveSdkVersion(ideaProject.getJdkName());

    JavaProjectData javaProjectData =
      new JavaProjectData(GradleConstants.SYSTEM_ID, compileOutputPath, jdkVersion, languageLevel, targetBytecodeVersion);

    projectNode.createChild(JavaProjectData.KEY, javaProjectData);
  }

  private void populateJavaModuleCompilerSettings(@NotNull IdeaModule ideaModule, @NotNull DataNode<ModuleData> moduleNode) {
    ExternalProject externalProject = resolverCtx.getExtraProject(ideaModule, ExternalProject.class);
    if (externalProject == null) return;
    if (resolverCtx.isResolveModulePerSourceSet()) {
      Map<ExternalSourceSet, DataNode<GradleSourceSetData>> sourceSets = findSourceSets(ideaModule, externalProject, moduleNode);
      for (Map.Entry<ExternalSourceSet, DataNode<GradleSourceSetData>> entry : sourceSets.entrySet()) {
        ExternalSourceSet sourceSet = entry.getKey();
        DataNode<GradleSourceSetData> sourceSetDataNode = entry.getValue();

        JavaModuleData moduleData = createSourceSetModuleData(ideaModule, sourceSet);
        sourceSetDataNode.createChild(JavaModuleData.KEY, moduleData);
        populateModuleSdkModel(ideaModule, sourceSetDataNode, sourceSet);
      }
    }
    populateModuleSdkModel(ideaModule, moduleNode, null);
    JavaModuleData moduleData = createMainModuleData(ideaModule, externalProject);
    moduleNode.createChild(JavaModuleData.KEY, moduleData);
  }

  private static @NotNull JavaModuleData createMainModuleData(@NotNull IdeaModule ideaModule, @NotNull ExternalProject externalProject) {
    LanguageLevel languageLevel = getLanguageLevel(ideaModule, externalProject);
    String targetBytecodeVersion = getTargetBytecodeVersion(ideaModule, externalProject);
    return new JavaModuleData(GradleConstants.SYSTEM_ID, languageLevel, targetBytecodeVersion);
  }

  private static @NotNull JavaModuleData createSourceSetModuleData(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    LanguageLevel languageLevel = getLanguageLevel(ideaModule, sourceSet);
    String targetBytecodeVersion = getTargetBytecodeVersion(ideaModule, sourceSet);
    return new JavaModuleData(GradleConstants.SYSTEM_ID, languageLevel, targetBytecodeVersion);
  }

  private @NotNull Map<ExternalSourceSet, DataNode<GradleSourceSetData>> findSourceSets(
    @NotNull IdeaModule ideaModule,
    @NotNull ExternalProject externalProject,
    @NotNull DataNode<ModuleData> moduleNode
  ) {
    Collection<DataNode<GradleSourceSetData>> sourceSetNodes = ExternalSystemApiUtil.getChildren(moduleNode, GradleSourceSetData.KEY);
    Map<String, DataNode<GradleSourceSetData>> sourceSetIndex = new LinkedHashMap<>();
    for (DataNode<GradleSourceSetData> sourceSetNode : sourceSetNodes) {
      sourceSetIndex.put(sourceSetNode.getData().getId(), sourceSetNode);
    }
    Map<ExternalSourceSet, DataNode<GradleSourceSetData>> result = new LinkedHashMap<>();
    for (ExternalSourceSet sourceSet : externalProject.getSourceSets().values()) {
      String moduleId = GradleProjectResolverUtil.getModuleId(resolverCtx, ideaModule, sourceSet);
      DataNode<GradleSourceSetData> sourceSetNode = sourceSetIndex.get(moduleId);
      if (sourceSetNode == null) continue;
      result.put(sourceSet, sourceSetNode);
    }
    return result;
  }

  private @NotNull List<Pair<IdeaModule, ExternalProject>> getExternalModules(@NotNull IdeaProject ideaProject) {
    return ideaProject.getModules().stream()
      .map(it -> new Pair<IdeaModule, ExternalProject>(it, resolverCtx.getExtraProject(it, ExternalProject.class)))
      .filter(it -> it.second != null)
      .collect(Collectors.toList());
  }

  private @Nullable LanguageLevel getLanguageLevel(@NotNull IdeaProject ideaProject) {
    List<Pair<IdeaModule, ExternalProject>> externalModules = getExternalModules(ideaProject);
    LanguageLevel languageLevel = externalModules.stream()
      .map(it -> getLanguageLevel(it.first, it.second))
      .filter(it -> it != null)
      .min(Comparator.naturalOrder())
      .orElse(null);
    if (languageLevel != null) return languageLevel;
    boolean isPreview = ContainerUtil.and(externalModules, it -> isPreview(it.second));
    IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview);
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull IdeaModule ideaModule, @NotNull ExternalProject externalProject) {
    boolean isPreview = isPreview(externalProject);
    LanguageLevel languageLevel = getLanguageLevel(externalProject, isPreview);
    if (languageLevel != null) return languageLevel;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview);
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    LanguageLevel languageLevel = getLanguageLevel(sourceSet);
    if (languageLevel != null) return languageLevel;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, sourceSet.isPreview());
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull ExternalSourceSet sourceSet) {
    String sourceCompatibility = sourceSet.getSourceCompatibility();
    if (sourceCompatibility == null) return null;
    return parseLanguageLevel(sourceCompatibility, sourceSet.isPreview());
  }

  @SuppressWarnings("SameParameterValue")
  private static @Nullable LanguageLevel getLanguageLevel(@NotNull ExternalProject externalProject, boolean isPreview) {
    String sourceCompatibility = externalProject.getSourceCompatibility();
    if (sourceCompatibility == null) return null;
    return parseLanguageLevel(sourceCompatibility, isPreview);
  }

  private static @Nullable LanguageLevel getLanguageLevel(@Nullable IdeaJavaLanguageSettings languageSettings, boolean isPreview) {
    if (languageSettings == null) return null;
    JavaVersion languageLevel = languageSettings.getLanguageLevel();
    if (languageLevel == null) return null;
    return parseLanguageLevel(languageLevel.toString(), isPreview);
  }

  private static @Nullable LanguageLevel parseLanguageLevel(@NotNull String languageLevelString, boolean isPreview) {
    LanguageLevel languageLevel = LanguageLevel.parse(languageLevelString);
    if (languageLevel == null) return null;
    return setPreview(languageLevel, isPreview);
  }

  private static @NotNull LanguageLevel setPreview(@NotNull LanguageLevel languageLevel, boolean isPreview) {
    if (languageLevel.isPreview() == isPreview) return languageLevel;
    com.intellij.util.lang.JavaVersion javaVersion = languageLevel.toJavaVersion();
    return Arrays.stream(LanguageLevel.values())
      .filter(it -> it.isPreview() == isPreview)
      .filter(it -> it.toJavaVersion().equals(javaVersion))
      .findFirst()
      .orElse(languageLevel);
  }

  private static boolean isPreview(@NotNull ExternalProject externalProject) {
    final Collection<? extends ExternalSourceSet> values = externalProject.getSourceSets().values();
    if (values.isEmpty()) {
      return false;
    } else {
      return ContainerUtil.and(values, it -> it.isPreview());
    }
  }

  private @Nullable String getTargetBytecodeVersion(@NotNull IdeaProject ideaProject) {
    String targetBytecodeVersion = getExternalModules(ideaProject).stream()
      .map(it -> getTargetBytecodeVersion(it.first, it.second))
      .filter(it -> it != null)
      .min(Comparator.naturalOrder())
      .orElse(null);
    if (targetBytecodeVersion != null) return targetBytecodeVersion;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
    return getTargetBytecodeVersion(javaLanguageSettings);
  }

  private static @Nullable String getTargetBytecodeVersion(@NotNull IdeaModule ideaModule, @NotNull ExternalProject externalProject) {
    String targetCompatibility = externalProject.getTargetCompatibility();
    if (targetCompatibility != null) return targetCompatibility;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getTargetBytecodeVersion(javaLanguageSettings);
  }

  private static @Nullable String getTargetBytecodeVersion(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    String targetCompatibility = sourceSet.getTargetCompatibility();
    if (targetCompatibility != null) return targetCompatibility;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getTargetBytecodeVersion(javaLanguageSettings);
  }

  private static @Nullable String getTargetBytecodeVersion(@Nullable IdeaJavaLanguageSettings languageSettings) {
    if (languageSettings == null) return null;
    JavaVersion targetByteCodeVersion = languageSettings.getTargetBytecodeVersion();
    if (targetByteCodeVersion == null) return null;
    return targetByteCodeVersion.toString();
  }

  private void populateProjectSdkModel(@NotNull IdeaProject ideaProject, @NotNull DataNode<? extends ProjectData> projectNode) {
    String jdkName = ideaProject.getJdkName();
    String sdkName = resolveSdkName(jdkName);
    ProjectSdkData projectSdkData = new ProjectSdkData(sdkName);
    projectNode.createChild(ProjectSdkData.KEY, projectSdkData);
  }

  private void populateModuleSdkModel(@NotNull IdeaModule ideaModule, @NotNull DataNode<? extends ModuleData> moduleNode,
                                      @Nullable ExternalSourceSet sourceSet) {
    try {
      String jdkName = ideaModule.getJdkName();
      String sdkName;
      if (jdkName != null || sourceSet == null || sourceSet.getJdkInstallationPath() == null) {
        sdkName = resolveSdkName(jdkName);
      } else {
        sdkName = lookupSdkByPath(sourceSet.getJdkInstallationPath());
      }
      ModuleSdkData moduleSdkData = new ModuleSdkData(sdkName);
      moduleNode.createChild(ModuleSdkData.KEY, moduleSdkData);
    }
    // todo[nskvortsov] the catch can be omitted when the support of the Gradle < 3.4 will be dropped
    catch (UnsupportedMethodException ignore) {
    }
  }

  private @Nullable String resolveSdkName(@Nullable String sdkName) {
    var gradleJvm = lookupGradleJvm(sdkName);
    if (gradleJvm != null) {
      return gradleJvm;
    }
    return lookupSdk(sdkName);
  }

  private @Nullable String lookupGradleJvm(@Nullable String sdkName) {
    var version = com.intellij.util.lang.JavaVersion.tryParse(sdkName);
    if (version != null) {
      var gradleJvm = getGradleJvm();
      if (gradleJvm != null) {
        var table = ProjectJdkTable.getInstance();
        var sdk = ReadAction.compute(() -> table.findJdk(gradleJvm));
        if (sdk != null) {
          var sdkVersion = com.intellij.util.lang.JavaVersion.tryParse(sdk.getVersionString());
          if (sdkVersion != null && sdkVersion.feature == version.feature) {
            return sdk.getName();
          }
        }
      }
    }
    return null;
  }

  private static @Nullable String lookupSdk(@Nullable String sdkName) {
    if (sdkName != null) {
      var sdk = SdkLookupUtil.lookupSdk(builder -> builder
        .withSdkName(sdkName)
        .withSdkType(ExternalSystemJdkUtil.getJavaSdkType())
        .onDownloadableSdkSuggested(__ -> SdkLookupDecision.STOP)
      );
      return sdk == null ? null : sdk.getName();
    }
    return null;
  }

  private static @NotNull String lookupSdkByPath(@NotNull String jdkInstallationPath) {
    String sdkName = ExternalSystemJdkProvider.getInstance().getJavaSdkType().suggestSdkName(null, jdkInstallationPath);
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    Sdk sdk = ReadAction.compute(() -> ContainerUtil.find(jdkTable.getAllJdks(),
                                                          candidate -> jdkInstallationPath.equals(candidate.getHomePath()) ||
                                                                       sdkName.equals(candidate.getName())));
    if (sdk != null) {
      return sdk.getName();
    }

    final Sdk effectiveSdk = ExternalSystemJdkProvider.getInstance().createJdk(sdkName, jdkInstallationPath);
    ApplicationManager.getApplication().invokeAndWait(() -> SdkConfigurationUtil.addSdk(effectiveSdk));
    return effectiveSdk.getName();
  }


  private @Nullable String getGradleJvm() {
    var settings = getProjectSettings();
    return settings == null ? null : settings.getGradleJvm();
  }

  private @Nullable GradleProjectSettings getProjectSettings() {
    var project = resolverCtx.getExternalSystemTaskId().findProject();
    if (project != null) {
      var settings = GradleSettings.getInstance(project);
      var linkedProjectPath = resolverCtx.getProjectPath();
      return settings.getLinkedProjectSettings(linkedProjectPath);
    }
    return null;
  }

}
