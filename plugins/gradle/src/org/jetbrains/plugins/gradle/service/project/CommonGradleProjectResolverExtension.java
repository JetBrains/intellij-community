// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension;
import com.intellij.openapi.externalSystem.model.ConfigurationDataImpl;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.util.io.CanonicalPathPrefixTreeFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision;
import com.intellij.openapi.roots.ui.configuration.SdkLookupUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.prefix.map.MutablePrefixTreeMap;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.lang.JavaVersion;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.*;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencySyncIssue;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.GradleProjectBuildScriptData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.model.tests.ExternalTestSourceMapping;
import org.jetbrains.plugins.gradle.model.tests.ExternalTestsModel;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleData;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.*;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.CONFIGURATION_ARTIFACTS;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.MODULES_OUTPUTS;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.*;

/**
 * {@link CommonGradleProjectResolverExtension} provides implementation of Gradle project resolver common to all project types.
 *
 * @author Vladislav.Soroka
 */
@Order(Integer.MAX_VALUE - 1)
public final class CommonGradleProjectResolverExtension extends AbstractProjectResolverExtension {
  private static final Logger LOG = Logger.getInstance(CommonGradleProjectResolverExtension.class);

  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  public static final String GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT = "gradle.version.catalogs.dynamic.support";

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    final ExternalProject externalProject = resolverCtx.getExtraProject(ExternalProject.class);
    if (externalProject != null) {
      ideProject.createChild(ExternalProjectDataCache.KEY, externalProject);
      ideProject.getData().setDescription(externalProject.getDescription());
    }

    final IntelliJSettings intellijSettings = resolverCtx.getExtraProject(IntelliJProjectSettings.class);
    if (intellijSettings != null) {
      ideProject.createChild(ProjectKeys.CONFIGURATION,
                             new ConfigurationDataImpl(GradleConstants.SYSTEM_ID, intellijSettings.getSettings()));
    }

    final DependencyAccessorsModel dependencyAccessorsModel = resolverCtx.getExtraProject(DependencyAccessorsModel.class);
    if (dependencyAccessorsModel != null && Registry.is(GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) {
      ideProject.createChild(BuildScriptClasspathData.ACCESSORS, dependencyAccessorsModel);
    }

    final VersionCatalogsModel versionCatalogsModel = resolverCtx.getExtraProject(VersionCatalogsModel.class);
    if (versionCatalogsModel != null) {
      ideProject.createChild(BuildScriptClasspathData.VERSION_CATALOGS, versionCatalogsModel);
    }
  }

  @NotNull
  @Override
  @SuppressWarnings("deprecation")
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    DataNode<ModuleData> mainModuleNode = createMainModule(resolverCtx, gradleModule, projectDataNode);
    final ModuleData mainModuleData = mainModuleNode.getData();
    final String mainModuleConfigPath = mainModuleData.getLinkedExternalProjectPath();
    final String mainModuleFileDirectoryPath = mainModuleData.getModuleFileDirectoryPath();
    final String jdkName = getJdkName(gradleModule);

    String[] moduleGroup = null;
    if (!resolverCtx.isUseQualifiedModuleNames()) {
      moduleGroup = getIdeModuleGroup(mainModuleData.getInternalName(), gradleModule);
      mainModuleData.setIdeModuleGroup(moduleGroup);
    }

    if (resolverCtx.isResolveModulePerSourceSet()) {
      ExternalProject externalProject = getExternalProject(gradleModule, resolverCtx);
      assert externalProject != null;
      for (ExternalSourceSet sourceSet : externalProject.getSourceSets().values()) {
        final String moduleId = getModuleId(resolverCtx, gradleModule, sourceSet);
        final String moduleExternalName = gradleModule.getName() + ":" + sourceSet.getName();
        final String moduleInternalName = getInternalModuleName(gradleModule, externalProject, sourceSet.getName(), resolverCtx);

        GradleSourceSetData sourceSetData = new GradleSourceSetData(
          moduleId, moduleExternalName, moduleInternalName, mainModuleFileDirectoryPath, mainModuleConfigPath);

        sourceSetData.setGroup(externalProject.getGroup());
        if ("main".equals(sourceSet.getName())) {
          sourceSetData.setPublication(new ProjectId(externalProject.getGroup(),
                                                     externalProject.getName(),
                                                     externalProject.getVersion()));
        }
        sourceSetData.setVersion(externalProject.getVersion());
        sourceSetData.setIdeModuleGroup(moduleGroup);

        sourceSetData.internalSetSourceCompatibility(sourceSet.getSourceCompatibility());
        sourceSetData.internalSetTargetCompatibility(sourceSet.getTargetCompatibility());
        sourceSetData.internalSetSdkName(jdkName);

        final Set<File> artifacts = FileCollectionFactory.createCanonicalFileSet();
        if ("main".equals(sourceSet.getName())) {
          final Set<File> defaultArtifacts = externalProject.getArtifactsByConfiguration().get("default");
          if (defaultArtifacts != null) {
            artifacts.addAll(defaultArtifacts);
          }
        }
        else {
          if ("test".equals(sourceSet.getName())) {
            sourceSetData.setProductionModuleId(getInternalModuleName(gradleModule, externalProject, "main", resolverCtx));
            final Set<File> testsArtifacts = externalProject.getArtifactsByConfiguration().get("tests");
            if (testsArtifacts != null) {
              artifacts.addAll(testsArtifacts);
            }
          }
        }
        artifacts.addAll(sourceSet.getArtifacts());
        for (ExternalSourceDirectorySet directorySet : sourceSet.getSources().values()) {
          artifacts.addAll(directorySet.getGradleOutputDirs());
        }
        sourceSetData.setArtifacts(new ArrayList<>(artifacts));

        DataNode<GradleSourceSetData> sourceSetDataNode = mainModuleNode.createChild(GradleSourceSetData.KEY, sourceSetData);
        final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
          projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);
        assert sourceSetMap != null;
        sourceSetMap.put(moduleId, Pair.create(sourceSetDataNode, sourceSet));
      }
    }
    else {
      try {
        IdeaJavaLanguageSettings languageSettings = gradleModule.getJavaLanguageSettings();
        if (languageSettings != null) {
          if (languageSettings.getLanguageLevel() != null) {
            mainModuleData.internalSetSourceCompatibility(languageSettings.getLanguageLevel().toString());
          }
          if (languageSettings.getTargetBytecodeVersion() != null) {
            mainModuleData.internalSetTargetCompatibility(languageSettings.getTargetBytecodeVersion().toString());
          }
        }
        mainModuleData.internalSetSdkName(jdkName);
      }
      // todo[Vlad] the catch can be omitted when the support of the Gradle < 3.0 will be dropped
      catch (UnsupportedMethodException ignore) {
        // org.gradle.tooling.model.idea.IdeaModule.getJavaLanguageSettings method supported since Gradle 2.11
      }
    }

    final ProjectData projectData = projectDataNode.getData();
    if (StringUtil.equals(mainModuleData.getLinkedExternalProjectPath(), projectData.getLinkedExternalProjectPath())) {
      projectData.setGroup(mainModuleData.getGroup());
      projectData.setVersion(mainModuleData.getVersion());
    }
    populateBuildScriptSource(gradleModule, mainModuleNode);

    return mainModuleNode;
  }

  private void populateBuildScriptSource(@NotNull IdeaModule ideaModule, @NotNull DataNode<? extends ModuleData> mainModuleNode) {
    try {
      File buildScriptSource = ideaModule.getGradleProject().getBuildScript().getSourceFile();
      GradleProjectBuildScriptData buildProjectData = new GradleProjectBuildScriptData(buildScriptSource);
      mainModuleNode.createChild(GradleProjectBuildScriptData.KEY, buildProjectData);
    }
    catch (UnsupportedMethodException ignore) {
    }
  }

  private static String @NotNull [] getIdeModuleGroup(String moduleName, IdeaModule gradleModule) {
    final String gradlePath = gradleModule.getGradleProject().getPath();
    final String rootName = gradleModule.getProject().getName();
    if (isEmpty(gradlePath) || ":".equals(gradlePath)) {
      return new String[]{moduleName};
    }
    else {
      return (rootName + gradlePath).split(":");
    }
  }

  @Nullable
  private static String getJdkName(@NotNull IdeaModule gradleModule) {
    try {
      return gradleModule.getJdkName();
    }
    catch (UnsupportedMethodException e) {
      return null;
    }
  }

  @Override
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    GradleExtensions gradleExtensions = resolverCtx.getExtraProject(gradleModule, GradleExtensions.class);
    if (gradleExtensions != null) {
      //noinspection ConditionCoveredByFurtherCondition
      boolean isGradleProxyClass = gradleExtensions instanceof Proxy || !(gradleExtensions instanceof DefaultGradleExtensions);
      DefaultGradleExtensions extensions = isGradleProxyClass ? new DefaultGradleExtensions(gradleExtensions)
                                                              : (DefaultGradleExtensions)gradleExtensions;
      ExternalProject externalProject = getExternalProject(gradleModule, resolverCtx);
      if (externalProject != null) {
        extensions.addTasks(externalProject.getTasks().values());
      }
      ideModule.createChild(GradleExtensionsDataService.KEY, extensions);
    }

    final IntelliJSettings intellijSettings = resolverCtx.getExtraProject(gradleModule, IntelliJSettings.class);
    if (intellijSettings != null) {
      ideModule.createChild(ProjectKeys.CONFIGURATION,
                            new ConfigurationDataImpl(GradleConstants.SYSTEM_ID, intellijSettings.getSettings()));
    }

    ProjectImportAction.AllModels models = resolverCtx.getModels();
    ExternalTestsModel externalTestsModel = models.getModel(gradleModule, ExternalTestsModel.class);
    if (externalTestsModel != null) {
      for (ExternalTestSourceMapping testSourceMapping : externalTestsModel.getTestSourceMappings()) {
        String testName = testSourceMapping.getTestName();
        String testTaskName = testSourceMapping.getTestTaskPath();
        Set<String> sourceFolders = testSourceMapping.getSourceFolders();
        TestData testData = new TestData(GradleConstants.SYSTEM_ID, testName, testTaskName, sourceFolders);
        ideModule.createChild(ProjectKeys.TEST, testData);
      }
    }
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule) {
    ExternalProject externalProject = getExternalProject(gradleModule, resolverCtx);
    Set<String> sourceSetContentRoots = Set.of();
    if (externalProject != null) {
      sourceSetContentRoots = addExternalProjectContentRoots(gradleModule, ideModule, externalProject);
    }

    MutablePrefixTreeMap<String, ContentRootData> contentRootIndex = CanonicalPathPrefixTreeFactory.INSTANCE.createMap();
    for (DataNode<ContentRootData> contentRootDataNode : ExternalSystemApiUtil.findAll(ideModule, ProjectKeys.CONTENT_ROOT)) {
      ContentRootData contentRootData = contentRootDataNode.getData();
      contentRootIndex.set(contentRootData.getRootPath(), contentRootData);
    }

    DomainObjectSet<? extends IdeaContentRoot> contentRoots = gradleModule.getContentRoots();
    if (contentRoots == null) return;
    for (IdeaContentRoot gradleContentRoot : contentRoots) {
      if (gradleContentRoot == null) continue;

      File rootDirectory = gradleContentRoot.getRootDirectory();
      if (rootDirectory == null) continue;

      boolean oldGradle = false;


      String contentRootPath = FileUtil.toCanonicalPath(rootDirectory.getPath());

      // don't add content root if one of source sets already have this content root
      boolean sameAsSourceSetContentRoot = sourceSetContentRoots.contains(contentRootPath);

      if (!sameAsSourceSetContentRoot) {
        ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, contentRootPath);
        contentRootIndex.set(contentRootPath, ideContentRoot);

        Set<File> excluded = gradleContentRoot.getExcludeDirectories();
        if (excluded != null) {
          for (File file : excluded) {
            ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.getPath());
          }
        }
      }

      boolean sourceSetsAreNotConfigured = sourceSetContentRoots.isEmpty();

      if (!resolverCtx.isResolveModulePerSourceSet() || sourceSetsAreNotConfigured) {
        List<? extends IdeaSourceDirectory> sourceDirectories = gradleContentRoot.getSourceDirectories().getAll();
        List<? extends IdeaSourceDirectory> testDirectories = gradleContentRoot.getTestDirectories().getAll();
        List<? extends IdeaSourceDirectory> resourceDirectories = Collections.emptyList();
        List<? extends IdeaSourceDirectory> testResourceDirectories = Collections.emptyList();
        try {
          final Set<File> notResourceDirs = collectExplicitNonResourceDirectories(externalProject);

          resourceDirectories = gradleContentRoot.getResourceDirectories().getAll();
          removeDuplicateResources(sourceDirectories, resourceDirectories, notResourceDirs);
          testResourceDirectories = gradleContentRoot.getTestResourceDirectories().getAll();
          removeDuplicateResources(testDirectories, testResourceDirectories, notResourceDirs);
        }
        catch (UnsupportedMethodException e) {
          oldGradle = true;
          // org.gradle.tooling.model.idea.IdeaContentRoot.getResourceDirectories/getTestResourceDirectories methods supported since Gradle 4.7
          LOG.debug(e.getMessage());

          if (externalProject == null) {
            populateContentRoot(contentRootIndex, ExternalSystemSourceType.SOURCE, gradleContentRoot.getSourceDirectories());
            populateContentRoot(contentRootIndex, ExternalSystemSourceType.TEST, gradleContentRoot.getTestDirectories());
          }
        }

        if (!oldGradle) {
          populateContentRoot(contentRootIndex, ExternalSystemSourceType.SOURCE, sourceDirectories);
          populateContentRoot(contentRootIndex, ExternalSystemSourceType.TEST, testDirectories);
          populateContentRoot(contentRootIndex, ExternalSystemSourceType.RESOURCE, resourceDirectories);
          populateContentRoot(contentRootIndex, ExternalSystemSourceType.TEST_RESOURCE, testResourceDirectories);
        }
      }
    }
    Set<String> existsContentRoots = new LinkedHashSet<>();
    for (DataNode<ContentRootData> contentRootDataNode : ExternalSystemApiUtil.findAll(ideModule, ProjectKeys.CONTENT_ROOT)) {
      ContentRootData contentRootData = contentRootDataNode.getData();
      existsContentRoots.add(contentRootData.getRootPath());
    }
    for (ContentRootData ideContentRoot : contentRootIndex.values()) {
      if (!existsContentRoots.contains(ideContentRoot.getRootPath())) {
        ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
      }
    }
  }

  @Nullable
  private static ExternalProject getExternalProject(@NotNull IdeaModule gradleModule, @NotNull ProjectResolverContext resolverCtx) {
    ExternalProject project = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (project == null && resolverCtx.isResolveModulePerSourceSet()) {
      LOG.error("External Project model is missing for module-per-sourceSet import mode. Please, check import log for error messages.");
    }
    return project;
  }

  private Set<String> addExternalProjectContentRoots(@NotNull IdeaModule gradleModule,
                                                     @NotNull DataNode<ModuleData> ideModule,
                                                     @NotNull ExternalProject externalProject) {
    Set<String> contentsRoots = new LinkedHashSet<>();
    final String buildDirPath = FileUtil.toCanonicalPath(externalProject.getBuildDir().getPath());

    processSourceSets(resolverCtx, gradleModule, externalProject, ideModule, new SourceSetsProcessor() {
      @Override
      public void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet) {
        sourceSet.getSources().forEach((key, sourceDirectorySet) -> {
          ExternalSystemSourceType sourceType = ExternalSystemSourceType.from(key);
          for (File file : sourceDirectorySet.getSrcDirs()) {
            String path = FileUtil.toCanonicalPath(file.getPath());
            contentsRoots.add(path);
            ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, path);
            if (FileUtil.isAncestor(path, buildDirPath, true)) {
              ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, buildDirPath);
            }
            ideContentRoot.storePath(sourceType, path);
            dataNode.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
          }
        });
      }
    });

    return contentsRoots;
  }

  private static void removeDuplicateResources(@NotNull List<? extends IdeaSourceDirectory> sourceDirectories,
                                               @NotNull List<? extends IdeaSourceDirectory> resourceDirectories,
                                               @NotNull Set<File> notResourceDirs) {


    resourceDirectories.removeIf(ideaSourceDirectory -> notResourceDirs.contains(ideaSourceDirectory.getDirectory()));
    removeAll(sourceDirectories, resourceDirectories);
  }

  @NotNull
  private static Set<File> collectExplicitNonResourceDirectories(@Nullable ExternalProject externalProject) {
    if (externalProject == null) {
      return Collections.emptySet();
    }

    return externalProject.getSourceSets().values().stream()
      .flatMap(ss -> ss.getSources().entrySet().stream()
        .filter(e -> !e.getKey().isResource())
        .flatMap(e -> e.getValue().getSrcDirs().stream()))
      .collect(Collectors.toCollection(() -> FileCollectionFactory.createCanonicalFileSet()));
  }

  private static void removeAll(List<? extends IdeaSourceDirectory> list, List<? extends IdeaSourceDirectory> toRemove) {
    Set<File> files = toRemove.stream().map(o -> o.getDirectory()).collect(Collectors.toSet());
    list.removeIf(o -> files.contains(o.getDirectory()));
  }

  private static void processSourceSets(@NotNull ProjectResolverContext resolverCtx,
                                        @NotNull IdeaModule gradleModule,
                                        @NotNull ExternalProject externalProject,
                                        @NotNull DataNode<ModuleData> ideModule,
                                        @NotNull SourceSetsProcessor processor) {
    Map<String, DataNode<GradleSourceSetData>> sourceSetsMap = new HashMap<>();
    for (DataNode<GradleSourceSetData> dataNode : ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)) {
      sourceSetsMap.put(dataNode.getData().getId(), dataNode);
    }

    for (ExternalSourceSet sourceSet : externalProject.getSourceSets().values()) {
      if (sourceSet == null || sourceSet.getSources().isEmpty()) continue;

      final String moduleId = getModuleId(resolverCtx, gradleModule, sourceSet);
      final DataNode<? extends ModuleData> moduleDataNode = sourceSetsMap.isEmpty() ? ideModule : sourceSetsMap.get(moduleId);
      if (moduleDataNode == null) continue;

      processor.process(moduleDataNode, sourceSet);
    }
  }


  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule) {
    ModuleData moduleData = ideModule.getData();
    moduleData.useExternalCompilerOutput(resolverCtx.isDelegatedBuild());

    File ideaOutDir = new File(moduleData.getLinkedExternalProjectPath(), "out");

    ExternalProject externalProject = getExternalProject(gradleModule, resolverCtx);
    if (resolverCtx.isResolveModulePerSourceSet()) {
      DataNode<ProjectData> projectDataNode = ideModule.getDataNode(ProjectKeys.PROJECT);
      assert projectDataNode != null;
      final Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap = projectDataNode.getUserData(MODULES_OUTPUTS);
      assert moduleOutputsMap != null;

      Set<String> outputDirs = new HashSet<>();
      assert externalProject != null;
      processSourceSets(resolverCtx, gradleModule, externalProject, ideModule, new SourceSetsProcessor() {
        @Override
        public void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet) {
          MultiMap<ExternalSystemSourceType, String> gradleOutputMap = dataNode.getUserData(GradleProjectResolver.GRADLE_OUTPUTS);
          if (gradleOutputMap == null) {
            gradleOutputMap = MultiMap.create();
            dataNode.putUserData(GradleProjectResolver.GRADLE_OUTPUTS, gradleOutputMap);
          }
          final ModuleData moduleData = dataNode.getData();
          moduleData.useExternalCompilerOutput(resolverCtx.isDelegatedBuild());
          for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> directorySetEntry
            : sourceSet.getSources().entrySet()) {
            ExternalSystemSourceType sourceType = ExternalSystemSourceType.from(directorySetEntry.getKey());
            ExternalSourceDirectorySet sourceDirectorySet = directorySetEntry.getValue();
            File ideOutputDir = getIdeOutputDir(sourceDirectorySet);
            File gradleOutputDir = getGradleOutputDir(sourceDirectorySet);
            File outputDir = resolverCtx.isDelegatedBuild() ? gradleOutputDir : ideOutputDir;
            moduleData.setCompileOutputPath(sourceType, ideOutputDir == null ? null : ideOutputDir.getPath());
            moduleData.setExternalCompilerOutputPath(sourceType, gradleOutputDir == null ? null : gradleOutputDir.getPath());
            moduleData.setInheritProjectCompileOutputPath(sourceDirectorySet.isCompilerOutputPathInherited());

            if (outputDir != null) {
              outputDirs.add(outputDir.getPath());
              for (File file : sourceDirectorySet.getGradleOutputDirs()) {
                String gradleOutputPath = ExternalSystemApiUtil.toCanonicalPath(file.getPath());
                gradleOutputMap.putValue(sourceType, gradleOutputPath);
                if (!file.getPath().equals(outputDir.getPath())) {
                  moduleOutputsMap.put(gradleOutputPath, Pair.create(moduleData.getId(), sourceType));
                }
              }
            }
          }
        }
      });
      if (outputDirs.stream().anyMatch(path -> FileUtil.isAncestor(ideaOutDir, new File(path), false))) {
        GradleUtil.excludeOutDir(ideModule, ideaOutDir);
      }
      return;
    }

    IdeaCompilerOutput moduleCompilerOutput = gradleModule.getCompilerOutput();
    boolean inheritOutputDirs = moduleCompilerOutput != null && moduleCompilerOutput.getInheritOutputDirs();

    if (moduleCompilerOutput != null) {
      File outputDir = moduleCompilerOutput.getOutputDir();
      if (outputDir != null) {
        moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, outputDir.getPath());
        moduleData.setCompileOutputPath(ExternalSystemSourceType.RESOURCE, outputDir.getPath());
        moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.SOURCE, outputDir.getPath());
        moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.RESOURCE, outputDir.getPath());
      }
      else {
        moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, new File(ideaOutDir, "production/classes").getPath());
        moduleData.setCompileOutputPath(ExternalSystemSourceType.RESOURCE, new File(ideaOutDir, "production/resources").getPath());
        if (externalProject != null) {
          File gradleOutputDir = getGradleOutputDir(externalProject, "main", ExternalSystemSourceType.SOURCE);
          moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.SOURCE,
                                                   gradleOutputDir == null ? null : gradleOutputDir.getPath());
          File gradleResourceOutputDir = getGradleOutputDir(externalProject, "main", ExternalSystemSourceType.RESOURCE);
          moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.RESOURCE,
                                                   gradleResourceOutputDir == null ? null : gradleResourceOutputDir.getPath());
        }
      }

      File testOutputDir = moduleCompilerOutput.getTestOutputDir();
      if (testOutputDir != null) {
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, testOutputDir.getPath());
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST_RESOURCE, testOutputDir.getPath());
        moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.TEST, testOutputDir.getPath());
        moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.TEST_RESOURCE, testOutputDir.getPath());
      }
      else {
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, new File(ideaOutDir, "test/classes").getPath());
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST_RESOURCE, new File(ideaOutDir, "test/resources").getPath());
        if (externalProject != null) {
          File gradleOutputDir = getGradleOutputDir(externalProject, "test", ExternalSystemSourceType.TEST);
          moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.TEST,
                                                   gradleOutputDir == null ? null : gradleOutputDir.getPath());
          File gradleResourceOutputDir = getGradleOutputDir(externalProject, "test", ExternalSystemSourceType.TEST_RESOURCE);
          moduleData.setExternalCompilerOutputPath(ExternalSystemSourceType.TEST_RESOURCE,
                                                   gradleResourceOutputDir == null ? null : gradleResourceOutputDir.getPath());
        }
      }

      if (!resolverCtx.isDelegatedBuild() && !inheritOutputDirs && (outputDir == null || testOutputDir == null)) {
        GradleUtil.excludeOutDir(ideModule, ideaOutDir);
      }
    }

    moduleData.setInheritProjectCompileOutputPath(inheritOutputDirs);
  }

  @Nullable
  public static File getGradleOutputDir(@NotNull ExternalProject externalProject,
                                        @NotNull String sourceSetName,
                                        @NotNull ExternalSystemSourceType sourceType) {
    ExternalSourceSet sourceSet = externalProject.getSourceSets().get(sourceSetName);
    if (sourceSet == null) return null;
    return getGradleOutputDir(sourceSet.getSources().get(sourceType));
  }

  @Nullable
  private static File getIdeOutputDir(@Nullable ExternalSourceDirectorySet sourceDirectorySet) {
    if (sourceDirectorySet == null) return null;
    return sourceDirectorySet.getOutputDir();
  }

  @Nullable
  private static File getGradleOutputDir(@Nullable ExternalSourceDirectorySet sourceDirectorySet) {
    if (sourceDirectorySet == null) return null;
    Set<File> srcDirs = sourceDirectorySet.getSrcDirs();
    Collection<File> outputDirectories = sourceDirectorySet.getGradleOutputDirs();
    String firstExistingLang = srcDirs.stream()
      .sorted()
      .filter(File::exists)
      .findFirst()
      .map(File::getName)
      .orElse(null);

    if (firstExistingLang == null) {
      return ContainerUtil.getFirstItem(outputDirectories);
    }

    return outputDirectories.stream()
      .filter(f -> f.getPath().contains(firstExistingLang))
      .findFirst()
      .orElse(ContainerUtil.getFirstItem(outputDirectories));
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull final DataNode<ProjectData> ideProject) {

    ExternalProject externalProject = getExternalProject(gradleModule, resolverCtx);
    if (resolverCtx.isResolveModulePerSourceSet()) {
      final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
        ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);
      final Map<String, String> artifactsMap = ideProject.getUserData(CONFIGURATION_ARTIFACTS);
      assert sourceSetMap != null;
      assert artifactsMap != null;
      assert externalProject != null;
      processSourceSets(resolverCtx, gradleModule, externalProject, ideModule, new SourceSetsProcessor() {
        @Override
        public void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet) {
          buildDependencies(resolverCtx, sourceSetMap, artifactsMap, dataNode, sourceSet.getDependencies(), ideProject);
        }
      });
      return;
    }

    final List<? extends IdeaDependency> dependencies = gradleModule.getDependencies().getAll();

    if (dependencies == null) return;

    List<String> orphanModules = new ArrayList<>();
    Map<String, ModuleData> modulesIndex = new HashMap<>();

    for (DataNode<ModuleData> dataNode : ExternalSystemApiUtil.getChildren(ideProject, ProjectKeys.MODULE)) {
      modulesIndex.put(dataNode.getData().getExternalName(), dataNode.getData());
    }

    for (int i = 0; i < dependencies.size(); i++) {
      IdeaDependency dependency = dependencies.get(i);
      if (dependency == null) {
        continue;
      }
      DependencyScope scope = parseScope(dependency.getScope());

      if (dependency instanceof IdeaModuleDependency) {
        ModuleDependencyData d = buildDependency(resolverCtx, ideModule, (IdeaModuleDependency)dependency, modulesIndex);
        d.setExported(dependency.getExported());
        if (scope != null) {
          d.setScope(scope);
        }
        d.setOrder(i);
        ideModule.createChild(ProjectKeys.MODULE_DEPENDENCY, d);
        ModuleData targetModule = d.getTarget();
        if (targetModule.getId().isEmpty() && targetModule.getLinkedExternalProjectPath().isEmpty()) {
          orphanModules.add(targetModule.getExternalName());
        }
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        LibraryDependencyData d = buildDependency(gradleModule, ideModule, (IdeaSingleEntryLibraryDependency)dependency, ideProject);
        d.setExported(dependency.getExported());
        if (scope != null) {
          d.setScope(scope);
        }
        d.setOrder(i);
        ideModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, d);
      }
    }

    if (!orphanModules.isEmpty()) {
      ExternalSystemTaskId taskId = resolverCtx.getExternalSystemTaskId();
      Project project = taskId.findProject();
      if (project != null) {
        String title = GradleBundle.message("gradle.project.resolver.orphan.modules.error.title");
        String targetOption = GradleBundle.message("gradle.settings.text.module.per.source.set",
                                                   ApplicationNamesInfo.getInstance().getFullProductName());
        String message = GradleBundle.message("gradle.project.resolver.orphan.modules.error.description",
                                              orphanModules.size(), join(orphanModules, ", "), targetOption);
        NotificationData notification = new NotificationData(title, message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
        ExternalSystemNotificationManager.getInstance(project).showNotification(taskId.getProjectSystemId(), notification);
      }
    }
  }

  @NotNull
  @Override
  public Collection<TaskData> populateModuleTasks(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule,
                                                  @NotNull DataNode<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException {

    final Collection<TaskData> tasks = new ArrayList<>();
    GradleModuleData gradleModuleData = new GradleModuleData(ideModule);
    final String moduleConfigPath = gradleModuleData.getGradleProjectDir();

    ExternalProject externalProject = getExternalProject(gradleModule, resolverCtx);
    if (externalProject != null) {
      String directoryToRunTask = gradleModuleData.getDirectoryToRunTask();
      boolean isSimpleTaskNameAllowed = directoryToRunTask.equals(moduleConfigPath);

      for (ExternalTask task : externalProject.getTasks().values()) {
        String taskGroup = task.getGroup();
        if (task.getName().trim().isEmpty() || isIdeaTask(task.getName(), taskGroup)) {
          continue;
        }
        boolean inherited = StringUtil.equals(task.getName(), task.getQName());
        String taskFullName;
        if (gradleModuleData.isIncludedBuild()) {
          if (inherited) {
            // running a task for all subprojects using the qualified task name is not supported for included builds
            continue;
          }
          taskFullName = gradleModuleData.getTaskPathOfSimpleTaskName(task.getName());
        }
        else {
          taskFullName = isSimpleTaskNameAllowed ? task.getName() : task.getQName();
        }

        String escapedTaskName = ParametersListUtil.escape(taskFullName);
        TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, escapedTaskName, directoryToRunTask, task.getDescription());
        taskData.setGroup(taskGroup);
        taskData.setType(task.getType());
        taskData.setTest(task.isTest());
        ideModule.createChild(ProjectKeys.TASK, taskData);
        taskData.setInherited(inherited);
        tasks.add(taskData);
      }
      return tasks;
    }

    for (GradleTask task : gradleModule.getGradleProject().getTasks()) {
      String taskName = task.getName();
      String taskGroup = getTaskGroup(task);
      if (taskName == null || taskName.trim().isEmpty() || isIdeaTask(taskName, taskGroup)) {
        continue;
      }
      TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, taskName, moduleConfigPath, task.getDescription());
      taskData.setGroup(taskGroup);
      ideModule.createChild(ProjectKeys.TASK, taskData);
      tasks.add(taskData);
    }

    return tasks;
  }

  @Nullable
  private static String getTaskGroup(GradleTask task) {
    String taskGroup;
    try {
      taskGroup = task.getGroup();
    }
    catch (UnsupportedMethodException e) {
      taskGroup = null;
    }
    return taskGroup;
  }

  @NotNull
  @Override
  public Set<Class<?>> getExtraProjectModelClasses() {
    return ContainerUtil.newLinkedHashSet(
      BuildScriptClasspathModel.class,
      GradleExtensions.class,
      ExternalTestsModel.class,
      IntelliJProjectSettings.class,
      IntelliJSettings.class,
      DependencyAccessorsModel.class,
      VersionCatalogsModel.class
    );
  }

  @NotNull
  @Override
  public ProjectImportModelProvider getModelProvider() {
    return new ClassSetImportModelProvider(getExtraProjectModelClasses(), ContainerUtil.newLinkedHashSet(ExternalProject.class, IdeaProject.class));
  }

  @Override
  public Set<Class<?>> getTargetTypes() {
    return ContainerUtil.newLinkedHashSet(
      ExternalProjectDependency.class,
      ExternalLibraryDependency.class,
      FileCollectionDependency.class,
      UnresolvedExternalDependency.class
    );
  }

  @Override
  public void enhanceTaskProcessing(@NotNull List<String> taskNames,
                                    @NotNull Consumer<String> initScriptConsumer,
                                    @NotNull Map<String, String> parameters) {
    String dispatchPort = parameters.get(GradleProjectResolverExtension.DEBUG_DISPATCH_PORT_KEY);
    if (dispatchPort == null) {
      return;
    }

    String debugOptions = parameters.get(GradleProjectResolverExtension.DEBUG_OPTIONS_KEY);
    if (debugOptions == null) {
      debugOptions = "";
    }
    List<String> lines = new ArrayList<>();

    String esRtJarPath = FileUtil.toCanonicalPath(PathManager.getJarPathForClass(ExternalSystemSourceType.class));
    lines.add("initscript { dependencies { classpath files(mapPath(\"" + esRtJarPath + "\")) } }"); // bring external-system-rt.jar

    for (DebuggerBackendExtension extension : DebuggerBackendExtension.EP_NAME.getExtensionList()) {
      lines.addAll(extension.initializationCode(dispatchPort, debugOptions));
    }

    final String script = join(lines, System.lineSeparator());
    initScriptConsumer.consume(script);
  }

  /**
   * Stores information about given directories at the corresponding to content root
   *
   * @param contentRootIndex index of content roots
   * @param type             type of data located at the given directories
   * @param dirs             directories which paths should be stored at the given content root
   * @throws IllegalArgumentException if specified by {@link ContentRootData#storePath(ExternalSystemSourceType, String)}
   */
  private static void populateContentRoot(@NotNull final MutablePrefixTreeMap<String, ContentRootData> contentRootIndex,
                                          @NotNull final ExternalSystemSourceType type,
                                          @Nullable final Iterable<? extends IdeaSourceDirectory> dirs)
    throws IllegalArgumentException {
    if (dirs == null) {
      return;
    }
    for (IdeaSourceDirectory dir : dirs) {
      ExternalSystemSourceType dirSourceType = type;
      try {
        if (dir.isGenerated() && !dirSourceType.isGenerated()) {
          final ExternalSystemSourceType generatedType = ExternalSystemSourceType.from(
            dirSourceType.isTest(), dir.isGenerated(), dirSourceType.isResource(), dirSourceType.isExcluded()
          );
          dirSourceType = generatedType != null ? generatedType : dirSourceType;
        }
      }
      catch (UnsupportedMethodException e) {
        LOG.warn(e.getMessage());
      }
      catch (Throwable e) {
        LOG.debug(e);
      }
      String path = FileUtil.toCanonicalPath(dir.getDirectory().getPath());
      List<String> contentRoots = new ArrayList<>(contentRootIndex.getAncestorKeys(path));
      if (contentRoots.isEmpty()) {
        ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, path);
        contentRootIndex.set(path, contentRootData);
        contentRoots.add(path);
      }
      String contentRootPath = ContainerUtil.getLastItem(contentRoots);
      assert contentRootPath != null;
      ContentRootData contentRoot = contentRootIndex.get(contentRootPath);
      assert contentRoot != null;
      contentRoot.storePath(dirSourceType, path);
    }
  }

  @Nullable
  private static DependencyScope parseScope(@Nullable IdeaDependencyScope scope) {
    if (scope == null) {
      return null;
    }
    String scopeAsString = scope.getScope();
    if (scopeAsString == null) {
      return null;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (scopeAsString.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return null;
  }

  @NotNull
  private static ModuleDependencyData buildDependency(@NotNull ProjectResolverContext resolverContext,
                                                      @NotNull DataNode<ModuleData> ownerModule,
                                                      @NotNull IdeaModuleDependency dependency,
                                                      @NotNull Map<String, ModuleData> registeredModulesIndex)
    throws IllegalStateException {

    final GradleExecutionSettings gradleExecutionSettings = resolverContext.getSettings();
    final String projectGradleVersionString = resolverContext.getProjectGradleVersion();
    if (gradleExecutionSettings != null && projectGradleVersionString != null) {
      final GradleVersion projectGradleVersion = GradleVersion.version(projectGradleVersionString);
      if (projectGradleVersion.compareTo(GradleVersion.version("4.0")) < 0) {
        final IdeaModule dependencyModule = getDependencyModuleByReflection(dependency);
        if (dependencyModule != null) {
          final ModuleData moduleData =
            gradleExecutionSettings.getExecutionWorkspace().findModuleDataByModule(resolverContext, dependencyModule);
          if (moduleData != null) {
            return new ModuleDependencyData(ownerModule.getData(), moduleData);
          }
        }
      }
    }


    final String moduleName = dependency.getTargetModuleName();

    if (gradleExecutionSettings != null) {
      ModuleData moduleData = gradleExecutionSettings.getExecutionWorkspace().findModuleDataByGradleModuleName(moduleName);
      if (moduleData != null) {
        return new ModuleDependencyData(ownerModule.getData(), moduleData);
      }
    }

    ModuleData registeredModuleData = registeredModulesIndex.get(moduleName);
    if (registeredModuleData != null) {
      return new ModuleDependencyData(ownerModule.getData(), registeredModuleData);
    }

    throw new IllegalStateException(String.format(
      "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. Registered modules: %s",
      dependency, moduleName, registeredModulesIndex.keySet()
    ));
  }

  @Nullable
  private static IdeaModule getDependencyModuleByReflection(@NotNull IdeaModuleDependency dependency) {
    Method getDependencyModule = ReflectionUtil.getMethod(dependency.getClass(), "getDependencyModule");
    if (getDependencyModule != null) {
      try {
        Object result = getDependencyModule.invoke(dependency);
        return (IdeaModule)result;
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOG.info("Failed to get dependency module for [" + dependency + "]", e);
      }
    }
    return null;
  }

  @NotNull
  private LibraryDependencyData buildDependency(@NotNull IdeaModule gradleModule,
                                                @NotNull DataNode<ModuleData> ownerModule,
                                                @NotNull IdeaSingleEntryLibraryDependency dependency,
                                                @NotNull DataNode<ProjectData> ideProject)
    throws IllegalStateException {
    File binaryPath = dependency.getFile();
    if (binaryPath == null) {
      throw new IllegalStateException(String.format(
        "Can't parse external library dependency '%s'. Reason: it doesn't specify path to the binaries", dependency
      ));
    }

    String libraryName;
    LibraryLevel level;
    final GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();

    // Gradle API doesn't explicitly provide information about unresolved libraries
    // original discussion http://issues.gradle.org/browse/GRADLE-1995
    // github issue https://github.com/gradle/gradle/issues/7733
    // That's why we use this dirty hack here.
    boolean unresolved = binaryPath.getName().startsWith(UNRESOLVED_DEPENDENCY_PREFIX);

    if (moduleVersion == null) {
      if (binaryPath.isFile()) {
        boolean isModuleLocalLibrary = false;
        try {
          isModuleLocalLibrary = FileUtil.isAncestor(gradleModule.getGradleProject().getProjectDirectory(), binaryPath, false);
        }
        catch (UnsupportedMethodException e) {
          // ignore, generate project-level library for the dependency
        }
        if (isModuleLocalLibrary) {
          level = LibraryLevel.MODULE;
        }
        else {
          level = LibraryLevel.PROJECT;
        }

        libraryName = chooseName(binaryPath, level, ideProject);
      }
      else {
        level = LibraryLevel.MODULE;
        libraryName = "";
      }

      if (unresolved) {
        // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
        libraryName = binaryPath.getName().substring(UNRESOLVED_DEPENDENCY_PREFIX.length());
        libraryName = join(split(libraryName, " "), ":");
      }
    }
    else {
      level = LibraryLevel.PROJECT;
      libraryName = String.format("%s:%s:%s", moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
      if (binaryPath.isFile()) {
        String libraryFileName = FileUtilRt.getNameWithoutExtension(binaryPath.getName());
        final String mavenLibraryFileName = String.format("%s-%s", moduleVersion.getName(), moduleVersion.getVersion());
        if (!mavenLibraryFileName.equals(libraryFileName)) {
          Pattern pattern = Pattern.compile(moduleVersion.getName() + "-" + moduleVersion.getVersion() + "-(.*)");
          Matcher matcher = pattern.matcher(libraryFileName);
          if (matcher.matches()) {
            final String classifier = matcher.group(1);
            libraryName += (":" + classifier);
          }
          else {
            final String artifactId = trimEnd(trimEnd(libraryFileName, moduleVersion.getVersion()), "-");
            libraryName = String.format("%s:%s:%s",
                                        moduleVersion.getGroup(),
                                        artifactId,
                                        moduleVersion.getVersion());
          }
        }
      }
    }

    // add packaging type to distinguish different artifact dependencies with same groupId:artifactId:version
    if (!unresolved && isNotEmpty(libraryName) && !FileUtilRt.extensionEquals(binaryPath.getName(), "jar")) {
      libraryName += (":" + FileUtilRt.getExtension(binaryPath.getName()));
    }

    LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, unresolved);
    if (moduleVersion != null) {
      library.setGroup(moduleVersion.getGroup());
      library.setArtifactId(moduleVersion.getName());
      library.setVersion(moduleVersion.getVersion());
    }

    if (!unresolved) {
      library.addPath(LibraryPathType.BINARY, binaryPath.getPath());
    }
    else {
      boolean isOfflineWork = resolverCtx.getSettings() != null && resolverCtx.getSettings().isOfflineWork();
      String message = String.format("Could not resolve %s.", libraryName);
      BuildIssue buildIssue = new UnresolvedDependencySyncIssue(
        libraryName, message, resolverCtx.getProjectPath(), isOfflineWork, ownerModule.getData().getId());
      resolverCtx.report(MessageEvent.Kind.ERROR, buildIssue);
    }

    File sourcePath = dependency.getSource();
    if (!unresolved && sourcePath != null) {
      library.addPath(LibraryPathType.SOURCE, sourcePath.getPath());
    }

    if (!unresolved && sourcePath == null) {
      attachGradleSdkSources(gradleModule, binaryPath, library, resolverCtx);
      if (resolverCtx instanceof DefaultProjectResolverContext) {
        attachSourcesAndJavadocFromGradleCacheIfNeeded(resolverCtx,
                                                       ((DefaultProjectResolverContext)resolverCtx).getGradleUserHome(), library);
      }
    }

    File javadocPath = dependency.getJavadoc();
    if (!unresolved && javadocPath != null) {
      library.addPath(LibraryPathType.DOC, javadocPath.getPath());
    }

    if (level == LibraryLevel.PROJECT && !linkProjectLibrary(ideProject, library)) {
      level = LibraryLevel.MODULE;
    }

    return new LibraryDependencyData(ownerModule.getData(), library, level);
  }

  private static String chooseName(File path,
                                   LibraryLevel level,
                                   DataNode<ProjectData> ideProject) {
    final String fileName = FileUtilRt.getNameWithoutExtension(path.getName());
    if (level == LibraryLevel.MODULE) {
      return fileName;
    }
    else {
      int count = 0;
      while (true) {
        String candidateName = fileName + (count == 0 ? "" : "_" + count);
        DataNode<LibraryData> libraryData =
          ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY,
                                     node -> node.getData().getExternalName().equals(candidateName));
        if (libraryData != null) {
          if (libraryData.getData().getPaths(LibraryPathType.BINARY).contains(FileUtil.toSystemIndependentName(path.getPath()))) {
            return candidateName;
          }
          else {
            count++;
          }
        }
        else {
          return candidateName;
        }
      }
    }
  }

  private interface SourceSetsProcessor {
    void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet);
  }
}
