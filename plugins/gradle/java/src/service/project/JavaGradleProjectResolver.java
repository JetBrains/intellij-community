// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.externalSystem.JavaModuleData;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleSdkData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectSdkData;
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.NioPathUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.lookupJdkByName;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.lookupJdkByPath;

/**
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class JavaGradleProjectResolver extends AbstractProjectResolverExtension {

  private final IdentityHashMap<GradleBuildScriptClasspathModel, List<BuildScriptClasspathData.ClasspathEntry>> buildScriptEntriesMap =
    new IdentityHashMap<>();

  @Override
  public void resolveFinished(@NotNull DataNode<ProjectData> projectDataNode) {
    buildScriptEntriesMap.clear();
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    populateJavaProjectCompilerSettings(gradleProject, ideProject);
    populateProjectSdkModel(gradleProject, ideProject);
    nextResolver.populateProjectExtraModels(gradleProject, ideProject);
  }

  private @NotNull String getCompileOutputPath() {
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
    }
    else {
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

  private static @NotNull AnnotationProcessingData getMergedAnnotationProcessingData(@NotNull AnnotationProcessingModel apModel) {

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

  private static @Nullable AnnotationProcessingData getAnnotationProcessingData(@NotNull AnnotationProcessingModel apModel,
                                                                                @NotNull String sourceSetName) {
    AnnotationProcessingConfig config = apModel.bySourceSetName(sourceSetName);
    if (config == null) {
      return null;
    }
    else {
      return AnnotationProcessingData.create(config.getAnnotationProcessorPath(),
                                             config.getAnnotationProcessorArguments());
    }
  }

  private void populateBuildScriptClasspathData(@NotNull IdeaModule gradleModule,
                                                @NotNull DataNode<ModuleData> ideModule) {
    final GradleBuildScriptClasspathModel
      buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, GradleBuildScriptClasspathModel.class);
    final List<BuildScriptClasspathData.ClasspathEntry> classpathEntries;
    if (buildScriptClasspathModel != null) {
      classpathEntries = buildScriptEntriesMap.computeIfAbsent(buildScriptClasspathModel, it -> ContainerUtil.map(
        buildScriptClasspathModel.getClasspath(),
        (Function<ClasspathEntryModel, BuildScriptClasspathData.ClasspathEntry>)model -> BuildScriptClasspathData.ClasspathEntry
          .create(model.getClasses(), model.getSources(), model.getJavadoc())));
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
  public @NotNull Set<Class<?>> getExtraProjectModelClasses() {
    return Set.of(AnnotationProcessingModel.class, ProjectDependencies.class);
  }

  private void populateJavaProjectCompilerSettings(@NotNull IdeaProject ideaProject, @NotNull DataNode<ProjectData> projectNode) {
    var compileOutputPath = getCompileOutputPath();
    var languageLevel = getLanguageLevel(ideaProject);
    var targetBytecodeVersion = getTargetBytecodeVersion(ideaProject);
    var compilerArguments = getCompilerArguments(ideaProject);
    var javaProjectData = new JavaProjectData(
      GradleConstants.SYSTEM_ID, compileOutputPath, languageLevel, targetBytecodeVersion, compilerArguments);

    javaProjectData.setJdkName(ideaProject.getJdkName());

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
    List<String> compilerArguments = getCompilerArguments(externalProject);
    return new JavaModuleData(GradleConstants.SYSTEM_ID, languageLevel, targetBytecodeVersion, compilerArguments);
  }

  private static @NotNull JavaModuleData createSourceSetModuleData(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    LanguageLevel languageLevel = getLanguageLevel(ideaModule, sourceSet);
    String targetBytecodeVersion = getTargetBytecodeVersion(ideaModule, sourceSet);
    List<String> compilerArguments = getCompilerArguments(sourceSet);
    return new JavaModuleData(GradleConstants.SYSTEM_ID, languageLevel, targetBytecodeVersion, compilerArguments);
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
    IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview(ideaProject));
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull IdeaModule ideaModule, @NotNull ExternalProject externalProject) {
    LanguageLevel languageLevel = getLanguageLevel(externalProject);
    if (languageLevel != null) return languageLevel;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview(externalProject));
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    LanguageLevel languageLevel = getLanguageLevel(sourceSet);
    if (languageLevel != null) return languageLevel;
    IdeaJavaLanguageSettings javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview(sourceSet));
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull ExternalSourceSet sourceSet) {
    String sourceCompatibility = sourceSet.getSourceCompatibility();
    if (sourceCompatibility == null) return null;
    return parseLanguageLevel(sourceCompatibility, isPreview(sourceSet));
  }

  private static @Nullable LanguageLevel getLanguageLevel(@NotNull ExternalProject externalProject) {
    String sourceCompatibility = externalProject.getSourceCompatibility();
    if (sourceCompatibility == null) return null;
    return parseLanguageLevel(sourceCompatibility, isPreview(externalProject));
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

  private boolean isPreview(@NotNull IdeaProject ideaProject) {
    return getCompilerArguments(ideaProject).contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
  }

  private static boolean isPreview(@NotNull ExternalProject externalProject) {
    return getCompilerArguments(externalProject).contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
  }

  private static boolean isPreview(@NotNull ExternalSourceSet sourceSet) {
    return getCompilerArguments(sourceSet).contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
  }

  private @NotNull List<String> getCompilerArguments(@NotNull IdeaProject ideaProject) {
    return getExternalModules(ideaProject).stream()
      .map(it -> getCompilerArguments(it.getSecond()))
      .min(Comparator.comparing(it -> it.size()))
      .orElse(Collections.emptyList());
  }

  private static @NotNull List<String> getCompilerArguments(@NotNull ExternalProject externalProject) {
    return externalProject.getSourceSets().values().stream()
      .map(it -> getCompilerArguments(it))
      .min(Comparator.comparing(it -> it.size()))
      .orElse(Collections.emptyList());
  }

  private static @NotNull List<String> getCompilerArguments(@NotNull ExternalSourceSet sourceSet) {
    return sourceSet.getCompilerArguments();
  }

  private void populateProjectSdkModel(@NotNull IdeaProject ideaProject, @NotNull DataNode<? extends ProjectData> projectNode) {
    Sdk sdk = lookupProjectSdk(ideaProject);
    String sdkName = ObjectUtils.doIfNotNull(sdk, it -> it.getName());
    ProjectSdkData projectSdkData = new ProjectSdkData(sdkName);
    projectNode.createChild(ProjectSdkData.KEY, projectSdkData);
  }

  private void populateModuleSdkModel(@NotNull IdeaModule ideaModule, @NotNull DataNode<? extends ModuleData> moduleNode,
                                      @Nullable ExternalSourceSet sourceSet) {
    try {
      Sdk sdk = lookupModuleSdk(ideaModule, sourceSet);
      String sdkName = ObjectUtils.doIfNotNull(sdk, it -> it.getName());
      ModuleSdkData moduleSdkData = new ModuleSdkData(sdkName);
      moduleNode.createChild(ModuleSdkData.KEY, moduleSdkData);
    }
    // todo[nskvortsov] the catch can be omitted when the support of the Gradle < 3.4 will be dropped
    catch (UnsupportedMethodException ignore) {
    }
  }

  private @Nullable Sdk lookupProjectSdk(@NotNull IdeaProject ideaProject) {
    String sdkName = ideaProject.getJdkName();
    if (sdkName != null) {
      return resolveSdkByName(sdkName);
    }
    return null;
  }

  private @Nullable Sdk lookupModuleSdk(@NotNull IdeaModule ideaModule, @Nullable ExternalSourceSet sourceSet) {
    String sdkName = ideaModule.getJdkName();
    if (sdkName != null) {
      return resolveSdkByName(sdkName);
    }
    File javaToolchainHome = ObjectUtils.doIfNotNull(sourceSet, it -> it.getJavaToolchainHome());
    if (javaToolchainHome != null) {
      return lookupJdkByPath(NioPathUtil.toCanonicalPath(javaToolchainHome.toPath()));
    }
    return null;
  }

  private @Nullable Sdk resolveSdkByName(@NotNull String sdkName) {
    var gradleJvm = lookupGradleJvm(sdkName);
    if (gradleJvm != null) {
      return gradleJvm;
    }
    return lookupJdkByName(sdkName);
  }

  private @Nullable Sdk lookupGradleJvm(@NotNull String sdkName) {
    var expectedSdkVersion = com.intellij.util.lang.JavaVersion.tryParse(sdkName);
    if (expectedSdkVersion == null) {
      return null;
    }
    var projectSettings = getProjectSettings();
    if (projectSettings == null) {
      return null;
    }
    var gradleJvm = projectSettings.getGradleJvm();
    if (gradleJvm == null) {
      return null;
    }
    var sdk = ProjectJdkTable.getInstance().findJdk(gradleJvm);
    if (sdk == null) {
      return null;
    }
    var actualSdkVersion = com.intellij.util.lang.JavaVersion.tryParse(sdk.getVersionString());
    if (actualSdkVersion == null) {
      return null;
    }
    if (actualSdkVersion.feature != expectedSdkVersion.feature) {
      return null;
    }
    return sdk;
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
