// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.NioPathUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingModel;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel;
import org.jetbrains.plugins.gradle.model.data.AnnotationProcessingData;
import org.jetbrains.plugins.gradle.model.data.AnnotationProcessingData.AnnotationProcessorOutput;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.matchJavaVersion;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class JavaGradleProjectResolver extends AbstractProjectResolverExtension {

  private final HashMap<GradleBuildScriptClasspathModel, List<BuildScriptClasspathData.ClasspathEntry>> buildScriptEntriesMap =
    new HashMap<>();

  @Override
  public void resolveFinished(@NotNull DataNode<ProjectData> projectDataNode) {
    buildScriptEntriesMap.clear();
  }

  @Override
  public @NotNull Set<Class<?>> getExtraProjectModelClasses() {
    return Set.of(AnnotationProcessingModel.class, ProjectDependencies.class);
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    populateJavaProjectCompilerSettings(gradleProject, ideProject);
    nextResolver.populateProjectExtraModels(gradleProject, ideProject);
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
    var apModel = resolverCtx.getProjectModel(gradleModule, AnnotationProcessingModel.class);
    if (apModel == null) {
      return;
    }
    if (!resolverCtx.isResolveModulePerSourceSet()) {
      var apData = getMergedAnnotationProcessingData(apModel);
      var dataNode = ideModule.createChild(AnnotationProcessingData.KEY, apData);
      populateAnnotationProcessingOutput(dataNode, apModel);
    }
    else {
      for (var node : ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)) {
        var apData = getAnnotationProcessingData(apModel, node.getData().getModuleName());
        if (apData != null) {
          var dataNode = node.createChild(AnnotationProcessingData.KEY, apData);
          populateAnnotationProcessorOutput(dataNode, apModel, node.getData().getModuleName());
        }
      }
    }
  }

  private static void populateAnnotationProcessorOutput(
    @NotNull DataNode<AnnotationProcessingData> parent,
    @NotNull AnnotationProcessingModel apModel,
    @NotNull String sourceSetName
  ) {
    var config = apModel.bySourceSetName(sourceSetName);
    if (config != null && config.getProcessorOutput() != null) {
      var annotationProcessorOutput = new AnnotationProcessorOutput(config.getProcessorOutput(), config.isTestSources());
      parent.createChild(AnnotationProcessingData.OUTPUT_KEY, annotationProcessorOutput);
    }
  }

  private static void populateAnnotationProcessingOutput(
    @NotNull DataNode<AnnotationProcessingData> parent,
    @NotNull AnnotationProcessingModel apModel
  ) {
    for (var config : apModel.allConfigs().values()) {
      if (config.getProcessorOutput() != null) {
        var annotationProcessorOutput = new AnnotationProcessorOutput(config.getProcessorOutput(), config.isTestSources());
        parent.createChild(AnnotationProcessingData.OUTPUT_KEY, annotationProcessorOutput);
      }
    }
  }

  private static @NotNull AnnotationProcessingData getMergedAnnotationProcessingData(@NotNull AnnotationProcessingModel apModel) {
    var mergedAnnotationProcessorPath = new LinkedHashSet<String>();
    for (var config : apModel.allConfigs().values()) {
      mergedAnnotationProcessorPath.addAll(config.getAnnotationProcessorPath());
    }

    var apArguments = new ArrayList<String>();
    var mainConfig = apModel.bySourceSetName("main");
    if (mainConfig != null) {
      apArguments.addAll(mainConfig.getAnnotationProcessorArguments());
    }

    return AnnotationProcessingData.create(mergedAnnotationProcessorPath, apArguments);
  }

  private static @Nullable AnnotationProcessingData getAnnotationProcessingData(
    @NotNull AnnotationProcessingModel apModel,
    @NotNull String sourceSetName
  ) {
    var config = apModel.bySourceSetName(sourceSetName);
    if (config == null) {
      return null;
    }
    return AnnotationProcessingData.create(config.getAnnotationProcessorPath(), config.getAnnotationProcessorArguments());
  }

  private void populateBuildScriptClasspathData(
    @NotNull IdeaModule gradleModule,
    @NotNull DataNode<ModuleData> ideModule
  ) {
    var buildScriptClasspathModel = resolverCtx.getProjectModel(gradleModule, GradleBuildScriptClasspathModel.class);
    var classpathEntries = ContainerUtil.<BuildScriptClasspathData.ClasspathEntry>emptyList();
    if (buildScriptClasspathModel != null) {
      classpathEntries = buildScriptEntriesMap.computeIfAbsent(buildScriptClasspathModel, model ->
        ContainerUtil.map(model.getClasspath(), it ->
          BuildScriptClasspathData.ClasspathEntry.create(it.getClasses(), it.getSources(), it.getJavadoc())
        )
      );
    }
    var buildScriptClasspathData = new BuildScriptClasspathData(SYSTEM_ID, classpathEntries);
    buildScriptClasspathData.setGradleHomeDir(buildScriptClasspathModel != null ? buildScriptClasspathModel.getGradleHomeDir() : null);
    ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);
  }

  private void populateDependenciesGraphData(
    @NotNull IdeaModule gradleModule,
    @NotNull DataNode<ModuleData> ideModule
  ) {
    var projectDependencies = resolverCtx.getProjectModel(gradleModule, ProjectDependencies.class);
    if (projectDependencies != null) {
      ideModule.createChild(ProjectKeys.DEPENDENCIES_GRAPH, projectDependencies);
    }
  }

  private void populateJavaProjectCompilerSettings(@NotNull IdeaProject ideaProject, @NotNull DataNode<ProjectData> projectNode) {
    projectNode.createChild(JavaProjectData.KEY, createProjectData(ideaProject));
    projectNode.createChild(ProjectSdkData.KEY, createProjectSdkData(ideaProject));
  }

  private @NotNull JavaProjectData createProjectData(@NotNull IdeaProject ideaProject) {
    var compileOutputPath = getProjectCompileOutputPath();
    var languageLevel = getProjectLanguageLevel(ideaProject);
    var targetBytecodeVersion = getProjectTargetBytecodeVersion(ideaProject);
    var compilerArguments = getProjectCompilerArguments(ideaProject);
    var javaProjectData = new JavaProjectData(SYSTEM_ID, compileOutputPath, languageLevel, targetBytecodeVersion, compilerArguments);

    javaProjectData.setJdkName(ideaProject.getJdkName());

    return javaProjectData;
  }

  private @NotNull String getProjectCompileOutputPath() {
    // Gradle API doesn't expose gradleProject compile output path yet.
    return resolverCtx.getProjectPath() + "/build/classes";
  }

  private void populateJavaModuleCompilerSettings(@NotNull IdeaModule ideaModule, @NotNull DataNode<ModuleData> moduleNode) {
    var sourceSetModel = resolverCtx.getProjectModel(ideaModule, GradleSourceSetModel.class);
    if (sourceSetModel == null) return;

    if (resolverCtx.isResolveModulePerSourceSet()) {
      var sourceSets = findSourceSets(ideaModule, sourceSetModel, moduleNode);
      for (var entry : sourceSets.entrySet()) {
        var sourceSet = entry.getKey();
        var sourceSetDataNode = entry.getValue();

        sourceSetDataNode.createChild(JavaModuleData.KEY, createSourceSetModuleData(ideaModule, sourceSet));
        sourceSetDataNode.createChild(ModuleSdkData.KEY, createSourceSetModuleSdkData(ideaModule, sourceSet));
      }
    }
    moduleNode.createChild(JavaModuleData.KEY, createHolderModuleData(ideaModule, sourceSetModel));
    moduleNode.createChild(ModuleSdkData.KEY, createHolderModuleSdkData(ideaModule, sourceSetModel));
  }

  private static @NotNull JavaModuleData createHolderModuleData(
    @NotNull IdeaModule ideaModule,
    @NotNull GradleSourceSetModel sourceSetModel
  ) {
    var languageLevel = getHolderModuleLanguageLevel(ideaModule, sourceSetModel);
    var targetBytecodeVersion = getHolderTargetBytecodeVersion(ideaModule, sourceSetModel);
    var compilerArguments = getHolderCompilerArguments(sourceSetModel);
    return new JavaModuleData(SYSTEM_ID, languageLevel, targetBytecodeVersion, compilerArguments);
  }

  private static @NotNull JavaModuleData createSourceSetModuleData(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    var languageLevel = getSourceSetModuleLanguageLevel(ideaModule, sourceSet);
    var targetBytecodeVersion = getSourceSetTargetBytecodeVersion(ideaModule, sourceSet);
    var compilerArguments = getSourceSetCompilerArguments(sourceSet);
    return new JavaModuleData(SYSTEM_ID, languageLevel, targetBytecodeVersion, compilerArguments);
  }

  private @NotNull Map<ExternalSourceSet, DataNode<GradleSourceSetData>> findSourceSets(
    @NotNull IdeaModule ideaModule,
    @NotNull GradleSourceSetModel sourceSetModel,
    @NotNull DataNode<ModuleData> moduleNode
  ) {
    var sourceSetNodes = ExternalSystemApiUtil.getChildren(moduleNode, GradleSourceSetData.KEY);
    var sourceSetIndex = new LinkedHashMap<String, DataNode<GradleSourceSetData>>();
    for (var sourceSetNode : sourceSetNodes) {
      sourceSetIndex.put(sourceSetNode.getData().getId(), sourceSetNode);
    }
    var result = new LinkedHashMap<ExternalSourceSet, DataNode<GradleSourceSetData>>();
    for (var sourceSet : sourceSetModel.getSourceSets().values()) {
      var moduleId = GradleProjectResolverUtil.getModuleId(resolverCtx, ideaModule, sourceSet);
      var sourceSetNode = sourceSetIndex.get(moduleId);
      if (sourceSetNode == null) continue;
      result.put(sourceSet, sourceSetNode);
    }
    return result;
  }

  private @NotNull Stream<? extends Pair<? extends IdeaModule, ? extends GradleSourceSetModel>> collectAllSourceSetModels(
    @NotNull IdeaProject ideaProject
  ) {
    return ideaProject.getModules().stream()
      .map(it -> ObjectUtils.doIfNotNull(resolverCtx.getProjectModel(it, GradleSourceSetModel.class), m -> new Pair<>(it, m)))
      .filter(Objects::nonNull);
  }

  private @Nullable LanguageLevel getProjectLanguageLevel(@NotNull IdeaProject ideaProject) {
    var languageLevel = collectAllSourceSetModels(ideaProject)
      .map(it -> getHolderModuleLanguageLevel(it.first, it.second))
      .filter(Objects::nonNull)
      .min(Comparator.naturalOrder())
      .orElse(null);
    if (languageLevel != null) return languageLevel;
    var javaLanguageSettings = ideaProject.getJavaLanguageSettings();
    var isPreview = getProjectCompilerArguments(ideaProject).contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
    return getLanguageLevel(javaLanguageSettings, isPreview);
  }

  private static @Nullable LanguageLevel getHolderModuleLanguageLevel(
    @NotNull IdeaModule ideaModule,
    @NotNull GradleSourceSetModel sourceSetModel
  ) {
    var isPreview = getHolderCompilerArguments(sourceSetModel).contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
    var languageLevel = parseLanguageLevel(sourceSetModel.getSourceCompatibility(), isPreview);
    if (languageLevel != null) return languageLevel;
    var javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview);
  }

  private static @Nullable LanguageLevel getSourceSetModuleLanguageLevel(
    @NotNull IdeaModule ideaModule,
    @NotNull ExternalSourceSet sourceSet
  ) {
    var isPreview = getSourceSetCompilerArguments(sourceSet).contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
    var languageLevel = parseLanguageLevel(sourceSet.getSourceCompatibility(), isPreview);
    if (languageLevel != null) return languageLevel;
    var javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getLanguageLevel(javaLanguageSettings, isPreview);
  }

  private static @Nullable LanguageLevel getLanguageLevel(@Nullable IdeaJavaLanguageSettings languageSettings, boolean isPreview) {
    if (languageSettings == null) return null;
    var languageLevel = languageSettings.getLanguageLevel();
    if (languageLevel == null) return null;
    return parseLanguageLevel(languageLevel.toString(), isPreview);
  }

  private static @Nullable LanguageLevel parseLanguageLevel(@Nullable String languageLevelString, boolean isPreview) {
    var languageLevel = LanguageLevel.parse(languageLevelString);
    if (languageLevel == null) return null;
    return setPreview(languageLevel, isPreview);
  }

  private static @NotNull LanguageLevel setPreview(@NotNull LanguageLevel languageLevel, boolean isPreview) {
    if (languageLevel.isPreview() == isPreview) return languageLevel;
    var javaVersion = languageLevel.toJavaVersion();
    return LanguageLevel.getEntries().stream()
      .filter(it -> it.isPreview() == isPreview)
      .filter(it -> it.toJavaVersion().equals(javaVersion))
      .findFirst()
      .orElse(languageLevel);
  }

  private @Nullable String getProjectTargetBytecodeVersion(@NotNull IdeaProject ideaProject) {
    var targetBytecodeVersion = collectAllSourceSetModels(ideaProject)
      .map(it -> getHolderTargetBytecodeVersion(it.first, it.second))
      .filter(Objects::nonNull)
      .min(Comparator.naturalOrder())
      .orElse(null);
    if (targetBytecodeVersion != null) return targetBytecodeVersion;
    var javaLanguageSettings = ideaProject.getJavaLanguageSettings();
    return getTargetBytecodeVersion(javaLanguageSettings);
  }

  private static @Nullable String getHolderTargetBytecodeVersion(
    @NotNull IdeaModule ideaModule,
    @NotNull GradleSourceSetModel sourceSetModel
  ) {
    var targetCompatibility = sourceSetModel.getTargetCompatibility();
    if (targetCompatibility != null) return targetCompatibility;
    var javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getTargetBytecodeVersion(javaLanguageSettings);
  }

  private static @Nullable String getSourceSetTargetBytecodeVersion(
    @NotNull IdeaModule ideaModule,
    @NotNull ExternalSourceSet sourceSet
  ) {
    var targetCompatibility = sourceSet.getTargetCompatibility();
    if (targetCompatibility != null) return targetCompatibility;
    var javaLanguageSettings = ideaModule.getJavaLanguageSettings();
    return getTargetBytecodeVersion(javaLanguageSettings);
  }

  private static @Nullable String getTargetBytecodeVersion(@Nullable IdeaJavaLanguageSettings languageSettings) {
    if (languageSettings == null) return null;
    var targetByteCodeVersion = languageSettings.getTargetBytecodeVersion();
    if (targetByteCodeVersion == null) return null;
    return targetByteCodeVersion.toString();
  }

  private @NotNull List<String> getProjectCompilerArguments(@NotNull IdeaProject ideaProject) {
    return collectAllSourceSetModels(ideaProject)
      .map(it -> getHolderCompilerArguments(it.getSecond()))
      .min(Comparator.comparing(it -> it.size()))
      .orElse(Collections.emptyList());
  }

  private static @NotNull List<String> getHolderCompilerArguments(@NotNull GradleSourceSetModel sourceSetModel) {
    return sourceSetModel.getSourceSets().values().stream()
      .map(it -> getSourceSetCompilerArguments(it))
      .min(Comparator.comparing(it -> it.size()))
      .orElse(Collections.emptyList());
  }

  private static @NotNull List<String> getSourceSetCompilerArguments(@NotNull ExternalSourceSet sourceSet) {
    return sourceSet.getCompilerArguments();
  }

  private @NotNull ProjectSdkData createProjectSdkData(@NotNull IdeaProject ideaProject) {
    var sdk = lookupProjectSdk(ideaProject);
    var sdkName = ObjectUtils.doIfNotNull(sdk, it -> it.getName());
    return new ProjectSdkData(sdkName);
  }

  private @NotNull ModuleSdkData createHolderModuleSdkData(@NotNull IdeaModule ideaModule, @NotNull GradleSourceSetModel sourceSetModel) {
    var sdk = lookupHolderModuleSdk(ideaModule, sourceSetModel);
    var sdkName = ObjectUtils.doIfNotNull(sdk, it -> it.getName());
    return new ModuleSdkData(sdkName);
  }

  private @NotNull ModuleSdkData createSourceSetModuleSdkData(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    var sdk = lookupSourceSetModuleSdk(ideaModule, sourceSet);
    var sdkName = ObjectUtils.doIfNotNull(sdk, it -> it.getName());
    return new ModuleSdkData(sdkName);
  }

  private @Nullable Sdk lookupProjectSdk(@NotNull IdeaProject ideaProject) {
    var sdk = collectAllSourceSetModels(ideaProject)
      .map(it -> lookupHolderModuleSdk(it.first, it.second))
      .filter(Objects::nonNull)
      .min(JavaSdkVersionUtil.naturalJavaSdkOrder(false))
      .orElse(null);
    if (sdk != null) {
      return sdk;
    }
    var sdkName = ideaProject.getJdkName();
    if (sdkName != null) {
      return lookupGradleJdkByName(sdkName);
    }
    return null;
  }

  private @Nullable Sdk lookupHolderModuleSdk(@NotNull IdeaModule ideaModule, @NotNull GradleSourceSetModel sourceSetModel) {
    var sdkName = ideaModule.getJdkName();
    if (sdkName != null) {
      return lookupGradleJdkByName(sdkName);
    }
    var toolchainVersion = sourceSetModel.getToolchainVersion();
    if (toolchainVersion != null) {
      return lookupGradleJdkByVersion(JavaVersion.compose(toolchainVersion));
    }
    var projectSdkName = ideaModule.getProject().getJdkName();
    if (projectSdkName != null) {
      return lookupGradleJdkByName(projectSdkName);
    }
    return null;
  }

  private @Nullable Sdk lookupSourceSetModuleSdk(@NotNull IdeaModule ideaModule, @NotNull ExternalSourceSet sourceSet) {
    var sdkName = ideaModule.getJdkName();
    if (sdkName != null) {
      return lookupGradleJdkByName(sdkName);
    }
    var javaToolchainHome = ObjectUtils.doIfNotNull(sourceSet, it -> it.getJavaToolchainHome());
    if (javaToolchainHome != null) {
      return lookupGradleJdkByPath(NioPathUtil.toCanonicalPath(javaToolchainHome.toPath()));
    }
    var projectSdkName = ideaModule.getProject().getJdkName();
    if (projectSdkName != null) {
      return lookupGradleJdkByName(projectSdkName);
    }
    return null;
  }

  private @Nullable Sdk lookupGradleJdkByName(@NotNull String sdkName) {
    var gradleJvm = lookupGradleJvmByName(sdkName);
    if (gradleJvm != null) return gradleJvm;
    return ExternalSystemJdkUtil.lookup(resolverCtx.getProject(), builder -> {
      return builder.withSdkName(sdkName);
    });
  }

  private @NotNull Sdk lookupGradleJdkByPath(@NotNull String sdkHome) {
    var gradleJvm = lookupGradleJvmByPath(sdkHome);
    if (gradleJvm != null) return gradleJvm;
    return ExternalSystemJdkUtil.lookupJdkByPath(resolverCtx.getProject(), sdkHome);
  }

  private @Nullable Sdk lookupGradleJdkByVersion(@NotNull JavaVersion sdkVersion) {
    var gradleJvm = lookupGradleJvmByVersion(sdkVersion);
    if (gradleJvm != null) return gradleJvm;
    return ExternalSystemJdkUtil.lookup(resolverCtx.getProject(), builder -> {
      return builder.withVersionFilter(it -> matchJavaVersion(sdkVersion, it));
    });
  }

  private @Nullable Sdk lookupGradleJvmByName(@NotNull String sdkName) {
    var javaVersion = JavaVersion.tryParse(sdkName);
    if (javaVersion == null) return null;
    return lookupGradleJvmByVersion(javaVersion);
  }

  private @Nullable Sdk lookupGradleJvmByPath(@NotNull String sdkHome) {
    var javaVersion = ExternalSystemJdkUtil.getJavaVersion(sdkHome);
    if (javaVersion == null) return null;
    return lookupGradleJvmByVersion(javaVersion);
  }

  private @Nullable Sdk lookupGradleJvmByVersion(@NotNull JavaVersion sdkVersion) {
    var sdk = lookupGradleJvm();
    if (sdk == null) return null;
    if (ExternalSystemJdkUtil.matchJavaVersion(sdkVersion, sdk.getVersionString())) {
      return sdk;
    }
    return null;
  }

  private @Nullable Sdk lookupGradleJvm() {
    var projectSettings = getProjectSettings();
    if (projectSettings == null) return null;
    var gradleJvm = projectSettings.getGradleJvm();
    if (gradleJvm == null) return null;
    return ExternalSystemJdkUtil.getJdk(resolverCtx.getProject(), gradleJvm);
  }

  private @Nullable GradleProjectSettings getProjectSettings() {
    var project = resolverCtx.getExternalSystemTaskId().findProject();
    if (project == null) return null;
    var settings = GradleSettings.getInstance(project);
    var linkedProjectPath = resolverCtx.getProjectPath();
    return settings.getLinkedProjectSettings(linkedProjectPath);
  }
}
