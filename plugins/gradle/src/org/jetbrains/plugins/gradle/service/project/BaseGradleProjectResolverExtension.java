/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.project;

import com.google.common.io.InputSupplier;
import com.google.gson.GsonBuilder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.text.CharArrayUtil;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.typehandling.ShortTypeHandling;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataService;
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;
import org.jetbrains.plugins.gradle.tooling.builder.ModelBuildScriptClasspathBuilderImpl;
import org.jetbrains.plugins.gradle.tooling.internal.init.Init;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.slf4j.impl.Log4jLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.CONFIGURATION_ARTIFACTS;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.MODULES_OUTPUTS;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.*;

/**
 * {@link BaseGradleProjectResolverExtension} provides base implementation of Gradle project resolver.
 *
 * @author Vladislav.Soroka
 * @since 10/14/13
 */
@Order(Integer.MAX_VALUE)
public class BaseGradleProjectResolverExtension implements GradleProjectResolverExtension {
  private static final Logger LOG = Logger.getInstance(BaseGradleProjectResolverExtension.class);

  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  @NotNull private ProjectResolverContext resolverCtx;
  @NotNull private final BaseProjectImportErrorHandler myErrorHandler = new BaseProjectImportErrorHandler();

  @Override
  public void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext) {
    resolverCtx = projectResolverContext;
  }

  @Override
  public void setNext(@NotNull GradleProjectResolverExtension next) {
    // should be the last extension in the chain
  }

  @Nullable
  @Override
  public GradleProjectResolverExtension getNext() {
    return null;
  }

  @NotNull
  @Override
  public ProjectData createProject() {
    final String projectDirPath = resolverCtx.getProjectPath();
    final ExternalProject externalProject = resolverCtx.getExtraProject(ExternalProject.class);
    String projectName = externalProject != null ? externalProject.getName() : resolverCtx.getModels().getIdeaProject().getName();
    return new ProjectData(GradleConstants.SYSTEM_ID, projectName, projectDirPath, projectDirPath);
  }

  @NotNull
  @Override
  public JavaProjectData createJavaProjectData() {
    final String projectDirPath = resolverCtx.getProjectPath();
    final IdeaProject ideaProject = resolverCtx.getModels().getIdeaProject();

    // Gradle API doesn't expose gradleProject compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    javaProjectData.setJdkVersion(ideaProject.getJdkName());
    LanguageLevel resolvedLanguageLevel = null;
    // org.gradle.tooling.model.idea.IdeaLanguageLevel.getLevel() returns something like JDK_1_6
    final String languageLevel = ideaProject.getLanguageLevel().getLevel();
    for (LanguageLevel level : LanguageLevel.values()) {
      if (level.name().equals(languageLevel)) {
        resolvedLanguageLevel = level;
        break;
      }
    }
    if (resolvedLanguageLevel != null) {
      javaProjectData.setLanguageLevel(resolvedLanguageLevel);
    }
    else {
      javaProjectData.setLanguageLevel(languageLevel);
    }
    return javaProjectData;
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    final ExternalProject externalProject = resolverCtx.getExtraProject(ExternalProject.class);
    if (externalProject != null) {
      ideProject.createChild(ExternalProjectDataService.KEY, externalProject);
      ideProject.getData().setDescription(externalProject.getDescription());
    }
  }

  @NotNull
  @Override
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    DataNode<ModuleData> mainModuleNode = createMainModule(resolverCtx, gradleModule, projectDataNode);
    final ModuleData mainModuleData = mainModuleNode.getData();
    final String mainModuleConfigPath = mainModuleData.getLinkedExternalProjectPath();
    final String mainModuleFileDirectoryPath = mainModuleData.getModuleFileDirectoryPath();
    final String jdkName = getJdkName(gradleModule);

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (resolverCtx.isResolveModulePerSourceSet() && externalProject != null) {
      String[] moduleGroup = null;
      if (!ModuleGrouperKt.isQualifiedModuleNamesEnabled()) {
        String gradlePath = gradleModule.getGradleProject().getPath();
        final boolean isRootModule = StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath);
        moduleGroup = isRootModule ? new String[]{mainModuleData.getInternalName()} : ArrayUtil.remove(gradlePath.split(":"), 0);
        mainModuleData.setIdeModuleGroup(isRootModule ? null : moduleGroup);
      }

      for (ExternalSourceSet sourceSet : externalProject.getSourceSets().values()) {
        final String moduleId = getModuleId(resolverCtx, gradleModule, sourceSet);
        final String moduleExternalName = gradleModule.getName() + ":" + sourceSet.getName();
        final String moduleInternalName = getInternalModuleName(gradleModule, externalProject, sourceSet.getName());

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

        sourceSetData.setSourceCompatibility(sourceSet.getSourceCompatibility());
        sourceSetData.setTargetCompatibility(sourceSet.getTargetCompatibility());
        sourceSetData.setSdkName(jdkName);

        final Set<File> artifacts = ContainerUtil.newTroveSet(FileUtil.FILE_HASHING_STRATEGY);
        if ("main".equals(sourceSet.getName())) {
          final Set<File> defaultArtifacts = externalProject.getArtifactsByConfiguration().get("default");
          if (defaultArtifacts != null) {
            artifacts.addAll(defaultArtifacts);
          }
          if (externalProject.getArtifactsByConfiguration().get("archives") != null) {
            final Set<File> archivesArtifacts = ContainerUtil.newHashSet(externalProject.getArtifactsByConfiguration().get("archives"));
            final Set<File> testsArtifacts = externalProject.getArtifactsByConfiguration().get("tests");
            if (testsArtifacts != null) {
              archivesArtifacts.removeAll(testsArtifacts);
            }
            artifacts.addAll(archivesArtifacts);
          }
        }
        else {
          if ("test".equals(sourceSet.getName())) {
            sourceSetData.setProductionModuleId(getInternalModuleName(gradleModule, externalProject, "main"));
            final Set<File> testsArtifacts = externalProject.getArtifactsByConfiguration().get("tests");
            if (testsArtifacts != null) {
              artifacts.addAll(testsArtifacts);
            }
          }
        }
        artifacts.addAll(sourceSet.getArtifacts());
        sourceSetData.setArtifacts(ContainerUtil.newArrayList(artifacts));

        DataNode<GradleSourceSetData> sourceSetDataNode = mainModuleNode.createChild(GradleSourceSetData.KEY, sourceSetData);
        final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
          projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);
        assert sourceSetMap != null;
        sourceSetMap.put(moduleId, Pair.create(sourceSetDataNode, sourceSet));
      }
    } else {
      try {
        IdeaJavaLanguageSettings languageSettings = gradleModule.getJavaLanguageSettings();
        if(languageSettings != null) {
          if(languageSettings.getLanguageLevel() != null) {
            mainModuleData.setSourceCompatibility(languageSettings.getLanguageLevel().toString());
          }
          if(languageSettings.getTargetBytecodeVersion() != null) {
            mainModuleData.setTargetCompatibility(languageSettings.getTargetBytecodeVersion().toString());
          }
        }
        mainModuleData.setSdkName(jdkName);
      }
      catch (UnsupportedMethodException ignore) {
        // org.gradle.tooling.model.idea.IdeaModule.getJavaLanguageSettings method supported since Gradle 2.11
      }
    }

    final ProjectData projectData = projectDataNode.getData();
    if (StringUtil.equals(mainModuleData.getLinkedExternalProjectPath(), projectData.getLinkedExternalProjectPath())) {
      projectData.setGroup(mainModuleData.getGroup());
      projectData.setVersion(mainModuleData.getVersion());
    }

    return mainModuleNode;
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
    final BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);
    final List<BuildScriptClasspathData.ClasspathEntry> classpathEntries;
    if (buildScriptClasspathModel != null) {
      classpathEntries = ContainerUtil.map(
        buildScriptClasspathModel.getClasspath(),
        (Function<ClasspathEntryModel, BuildScriptClasspathData.ClasspathEntry>)model -> new BuildScriptClasspathData.ClasspathEntry(model.getClasses(), model.getSources(), model.getJavadoc()));
    }
    else {
      classpathEntries = ContainerUtil.emptyList();
    }
    BuildScriptClasspathData buildScriptClasspathData = new BuildScriptClasspathData(GradleConstants.SYSTEM_ID, classpathEntries);
    buildScriptClasspathData.setGradleHomeDir(buildScriptClasspathModel != null ? buildScriptClasspathModel.getGradleHomeDir() : null);
    ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);

    GradleExtensions gradleExtensions = resolverCtx.getExtraProject(gradleModule, GradleExtensions.class);
    if (gradleExtensions != null) {
      DefaultGradleExtensions extensions = new DefaultGradleExtensions(gradleExtensions);
      ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
      if (externalProject != null) {
        extensions.getTasks().addAll(externalProject.getTasks().values());
      }
      ideModule.createChild(GradleExtensionsDataService.KEY, extensions);
    }
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule) {
    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      processSourceSets(resolverCtx, gradleModule, externalProject, ideModule, new SourceSetsProcessor() {
        @Override
        public void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet) {
          for (Map.Entry<IExternalSystemSourceType, ExternalSourceDirectorySet> directorySetEntry : sourceSet.getSources().entrySet()) {
            ExternalSystemSourceType sourceType = ExternalSystemSourceType.from(directorySetEntry.getKey());
            ExternalSourceDirectorySet sourceDirectorySet = directorySetEntry.getValue();

            for (File file : sourceDirectorySet.getSrcDirs()) {
              ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, file.getAbsolutePath());
              ideContentRoot.storePath(sourceType, file.getAbsolutePath());
              dataNode.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
            }
          }
        }
      });
    }

    DomainObjectSet<? extends IdeaContentRoot> contentRoots = gradleModule.getContentRoots();
    if (contentRoots == null) {
      return;
    }
    for (IdeaContentRoot gradleContentRoot : contentRoots) {
      if (gradleContentRoot == null) continue;

      File rootDirectory = gradleContentRoot.getRootDirectory();
      if (rootDirectory == null) continue;

      ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.getAbsolutePath());
      if (externalProject == null) {
        populateContentRoot(ideContentRoot, ExternalSystemSourceType.SOURCE, gradleContentRoot.getSourceDirectories());
        populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST, gradleContentRoot.getTestDirectories());

        if (gradleContentRoot instanceof ExtIdeaContentRoot) {
          ExtIdeaContentRoot extIdeaContentRoot = (ExtIdeaContentRoot)gradleContentRoot;
          populateContentRoot(ideContentRoot, ExternalSystemSourceType.RESOURCE, extIdeaContentRoot.getResourceDirectories());
          populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST_RESOURCE, extIdeaContentRoot.getTestResourceDirectories());
        }
      }

      Set<File> excluded = gradleContentRoot.getExcludeDirectories();
      if (excluded != null) {
        for (File file : excluded) {
          ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.getAbsolutePath());
        }
      }
      ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
    }
  }

  private static void processSourceSets(@NotNull ProjectResolverContext resolverCtx,
                                        @NotNull IdeaModule gradleModule,
                                        @NotNull ExternalProject externalProject,
                                        @NotNull DataNode<ModuleData> ideModule,
                                        @NotNull SourceSetsProcessor processor) {
    Map<String, DataNode<GradleSourceSetData>> sourceSetsMap = ContainerUtil.newHashMap();
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
    File ideaOutDir = new File(moduleData.getLinkedExternalProjectPath(), "out");

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (resolverCtx.isResolveModulePerSourceSet() && externalProject != null) {
      DataNode<ProjectData> projectDataNode = ideModule.getDataNode(ProjectKeys.PROJECT);
      assert projectDataNode != null;
      final Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap = projectDataNode.getUserData(MODULES_OUTPUTS);
      assert moduleOutputsMap != null;

      Set<String> outputDirs = ContainerUtil.newHashSet();
      processSourceSets(resolverCtx, gradleModule, externalProject, ideModule, new SourceSetsProcessor() {
        @Override
        public void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet) {
          for (Map.Entry<IExternalSystemSourceType, ExternalSourceDirectorySet> directorySetEntry : sourceSet.getSources().entrySet()) {
            ExternalSystemSourceType sourceType = ExternalSystemSourceType.from(directorySetEntry.getKey());
            ExternalSourceDirectorySet sourceDirectorySet = directorySetEntry.getValue();
            final ModuleData moduleData = dataNode.getData();
            File outputDir = sourceDirectorySet.getOutputDir();
            outputDirs.add(outputDir.getPath());
            moduleData.setCompileOutputPath(sourceType, outputDir.getAbsolutePath());
            moduleData.setInheritProjectCompileOutputPath(sourceDirectorySet.isCompilerOutputPathInherited());


            String gradleOutputPath = moduleData.getCompileOutputPath(sourceType);
            for (File gradleOutputDir : sourceDirectorySet.getGradleOutputDirs()) {
              if(!gradleOutputDir.getPath().equals(outputDir.getPath())) {
                gradleOutputPath = ExternalSystemApiUtil.toCanonicalPath(gradleOutputDir.getAbsolutePath());
                moduleOutputsMap.put(gradleOutputPath, Pair.create(moduleData.getId(), sourceType));
              }
            }

            Map<ExternalSystemSourceType, String> map = dataNode.getUserData(GradleProjectResolver.GRADLE_OUTPUTS);
            if(map == null) {
              map = ContainerUtil.newHashMap();
              dataNode.putUserData(GradleProjectResolver.GRADLE_OUTPUTS, map);
            }
            map.put(sourceType, gradleOutputPath);
          }
        }
      });
      if (outputDirs.stream().anyMatch(path -> FileUtil.isAncestor(ideaOutDir, new File(path), false))) {
        excludeOutDir(ideModule, ideaOutDir);
      }
      return;
    }

    IdeaCompilerOutput moduleCompilerOutput = gradleModule.getCompilerOutput();
    Map<ExternalSystemSourceType, File> compileOutputPaths = ContainerUtil.newHashMap();
    boolean inheritOutputDirs = moduleCompilerOutput != null && moduleCompilerOutput.getInheritOutputDirs();

    if (moduleCompilerOutput != null) {
      File outputDir = moduleCompilerOutput.getOutputDir();
      File classesOutputDir = ObjectUtils.chooseNotNull(outputDir, new File(ideaOutDir, "production/classes"));
      compileOutputPaths.put(ExternalSystemSourceType.SOURCE, classesOutputDir);
      File resourcesOutputDir = ObjectUtils.chooseNotNull(outputDir, new File(ideaOutDir, "production/resources"));
      compileOutputPaths.put(ExternalSystemSourceType.RESOURCE, resourcesOutputDir);

      File testOutputDir = moduleCompilerOutput.getTestOutputDir();
      File testClassesOutputDir = ObjectUtils.chooseNotNull(testOutputDir, new File(ideaOutDir, "test/classes"));
      compileOutputPaths.put(ExternalSystemSourceType.TEST, testClassesOutputDir);
      File testResourcesOutputDir = ObjectUtils.chooseNotNull(testOutputDir, new File(ideaOutDir, "test/resources"));
      compileOutputPaths.put(ExternalSystemSourceType.TEST_RESOURCE, testResourcesOutputDir);

      if (!inheritOutputDirs && (outputDir == null || testOutputDir == null)) {
        excludeOutDir(ideModule, ideaOutDir);
      }
    }

    for (Map.Entry<ExternalSystemSourceType, File> sourceTypeFileEntry : compileOutputPaths.entrySet()) {
      final File outputPath = sourceTypeFileEntry.getValue();
      if (outputPath != null) {
        moduleData.setCompileOutputPath(sourceTypeFileEntry.getKey(), outputPath.getAbsolutePath());
      }
    }

    moduleData.setInheritProjectCompileOutputPath(inheritOutputDirs);
  }

  private void excludeOutDir(@NotNull DataNode<ModuleData> ideModule, File ideaOutDir) {
    ContentRootData excludedContentRootData;
    DataNode<ContentRootData> contentRootDataDataNode = ExternalSystemApiUtil.find(ideModule, ProjectKeys.CONTENT_ROOT);
    if (contentRootDataDataNode == null ||
        !FileUtil.isAncestor(new File(contentRootDataDataNode.getData().getRootPath()), ideaOutDir, false)) {
      excludedContentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, ideaOutDir.getAbsolutePath());
    }
    else {
      excludedContentRootData = contentRootDataDataNode.getData();
    }

    excludedContentRootData.storePath(ExternalSystemSourceType.EXCLUDED, ideaOutDir.getAbsolutePath());
    ideModule.createChild(ProjectKeys.CONTENT_ROOT, excludedContentRootData);
  }

  @Nullable
  private static File selectCompileOutputDir(@Nullable File outputDir, @NotNull String projectPath, String path) {
    return outputDir != null ? outputDir : new File(projectPath, path);
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull final DataNode<ProjectData> ideProject) {

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
        ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);

      final Map<String, String> artifactsMap = ideProject.getUserData(CONFIGURATION_ARTIFACTS);
      assert artifactsMap != null;

      if (resolverCtx.isResolveModulePerSourceSet()) {
        assert sourceSetMap != null;
        processSourceSets(resolverCtx, gradleModule, externalProject, ideModule, new SourceSetsProcessor() {
          @Override
          public void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet) {
            buildDependencies(resolverCtx, sourceSetMap, artifactsMap, dataNode, sourceSet.getDependencies(), ideProject);
          }
        });

        return;
      }
    }

    final List<? extends IdeaDependency> dependencies = gradleModule.getDependencies().getAll();

    if (dependencies == null) return;

    List<String> orphanModules = ContainerUtil.newArrayList();
    for (int i = 0; i < dependencies.size(); i++) {
      IdeaDependency dependency = dependencies.get(i);
      if (dependency == null) {
        continue;
      }
      DependencyScope scope = parseScope(dependency.getScope());

      if (dependency instanceof IdeaModuleDependency) {
        ModuleDependencyData d = buildDependency(resolverCtx, ideModule, (IdeaModuleDependency)dependency, ideProject);
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
        String msg =
          "Can't find the following module" + (orphanModules.size() > 1 ? "s" : "") + ": " + StringUtil.join(orphanModules, ", ")
          + "\nIt can be caused by composite build configuration inside your *.gradle scripts with Gradle version older than 3.3." +
          "\nTry Gradle 3.3 or better or enable 'Create separate module per source set' option";
        NotificationData notification = new NotificationData(
          "Gradle project structure problems", msg, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
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

    final Collection<TaskData> tasks = ContainerUtil.newArrayList();
    final String moduleConfigPath = ideModule.getData().getLinkedExternalProjectPath();

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    String rootProjectPath = ideProject.getData().getLinkedExternalProjectPath();
    try {
      GradleBuild build = resolverCtx.getExtraProject(gradleModule, GradleBuild.class);
      if (build != null) {
        rootProjectPath = ExternalSystemApiUtil.toCanonicalPath(build.getRootProject().getProjectDirectory().getCanonicalPath());
      }
    }
    catch (IOException e) {
      LOG.warn("construction of the canonical path for the module fails", e);
    }

    final boolean isFlatProject = !FileUtil.isAncestor(rootProjectPath, moduleConfigPath, false);
    if (externalProject != null) {
      for (ExternalTask task : externalProject.getTasks().values()) {
        String taskName = isFlatProject ? task.getQName() : task.getName();
        String taskGroup = task.getGroup();
        if (taskName.trim().isEmpty() || isIdeaTask(taskName, taskGroup)) {
          continue;
        }
        final String taskPath = isFlatProject ? rootProjectPath : moduleConfigPath;
        TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, taskName, taskPath, task.getDescription());
        taskData.setGroup(taskGroup);
        taskData.setType(task.getType());
        ideModule.createChild(ProjectKeys.TASK, taskData);
        taskData.setInherited(StringUtil.equals(task.getName(), task.getQName()));
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
  public Set<Class> getExtraProjectModelClasses() {
    Set<Class> result = ContainerUtil.set(GradleBuild.class, ModuleExtendedModel.class);
    result.add(BuildScriptClasspathModel.class);
    result.add(GradleExtensions.class);
    result.add(ExternalProject.class);
    return result;
  }

  @NotNull
  @Override
  public Set<Class> getToolingExtensionsClasses() {
    return ContainerUtil.set(
      // external-system-rt.jar
      ExternalSystemSourceType.class,
      // gradle-tooling-extension-api jar
      ProjectImportAction.class,
      // gradle-tooling-extension-impl jar
      ModelBuildScriptClasspathBuilderImpl.class,
      // guava-jdk5-17.0.jar, removed (in future guava versions) class has to be used
      // to prevent populating gradle daemon classpath with incompatible guava
      InputSupplier.class,
      GsonBuilder.class,
      ShortTypeHandling.class
    );
  }

  @NotNull
  @Override
  public List<Pair<String, String>> getExtraJvmArgs() {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      final List<Pair<String, String>> extraJvmArgs = ContainerUtil.newArrayList();

      final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      if (!StringUtil.isEmpty(httpConfigurable.PROXY_EXCEPTIONS)) {
        List<String> hosts = StringUtil.split(httpConfigurable.PROXY_EXCEPTIONS, ",");
        if (!hosts.isEmpty()) {
          final String nonProxyHosts = StringUtil.join(hosts, StringUtil.TRIMMER, "|");
          extraJvmArgs.add(pair("http.nonProxyHosts", nonProxyHosts));
          extraJvmArgs.add(pair("https.nonProxyHosts", nonProxyHosts));
        }
      }
      if (httpConfigurable.USE_HTTP_PROXY && StringUtil.isNotEmpty(httpConfigurable.getProxyLogin())) {
        extraJvmArgs.add(pair("http.proxyUser", httpConfigurable.getProxyLogin()));
        extraJvmArgs.add(pair("https.proxyUser", httpConfigurable.getProxyLogin()));
        final String plainProxyPassword = httpConfigurable.getPlainProxyPassword();
        extraJvmArgs.add(pair("http.proxyPassword", plainProxyPassword));
        extraJvmArgs.add(pair("https.proxyPassword", plainProxyPassword));
      }
      extraJvmArgs.addAll(httpConfigurable.getJvmProperties(false, null));

      return extraJvmArgs;
    }

    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<String> getExtraCommandLineArgs() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    return myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @Override
  public void preImportCheck() {
  }

  @Override
  public void enhanceTaskProcessing(@NotNull List<String> taskNames,
                                    @Nullable String jvmAgentSetup,
                                    @NotNull Consumer<String> initScriptConsumer) {
    if (!StringUtil.isEmpty(jvmAgentSetup)) {
      final String names = "[\"" + StringUtil.join(taskNames, "\", \"") + "\"]";
      final String[] lines = {
        "gradle.taskGraph.beforeTask { Task task ->",
        "    if (task instanceof JavaForkOptions && (" + names + ".contains(task.name) || " + names + ".contains(task.path))) {",
        "        def jvmArgs = task.jvmArgs.findAll{!it?.startsWith('-agentlib:jdwp') && !it?.startsWith('-Xrunjdwp')}",
        "        jvmArgs << '" + jvmAgentSetup.trim().replace("\\", "\\\\") + '\'',
        "        task.jvmArgs = jvmArgs",
        "    }" +
        "}",
      };
      final String script = StringUtil.join(lines, SystemProperties.getLineSeparator());
      initScriptConsumer.consume(script);
    }
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) throws ExecutionException {
    PathsList classPath = parameters.getClassPath();

    // Gradle i18n bundle.
    ExternalSystemApiUtil.addBundle(classPath, GradleBundle.PATH_TO_BUNDLE, GradleBundle.class);

    // Gradle tool jars.
    String toolingApiPath = PathManager.getJarPathForClass(ProjectConnection.class);
    if (toolingApiPath == null) {
      LOG.warn(GradleBundle.message("gradle.generic.text.error.jar.not.found"));
      throw new ExecutionException("Can't find gradle libraries");
    }
    File gradleJarsDir = new File(toolingApiPath).getParentFile();
    File[] gradleJars = gradleJarsDir.listFiles(FileFilters.filesWithExtension("jar"));
    if (gradleJars == null) {
      LOG.warn(GradleBundle.message("gradle.generic.text.error.jar.not.found"));
      throw new ExecutionException("Can't find gradle libraries at " + gradleJarsDir.getAbsolutePath());
    }
    for (File jar : gradleJars) {
      classPath.add(jar.getAbsolutePath());
    }

    List<String> additionalEntries = ContainerUtilRt.newArrayList();
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(GroovyObject.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(GsonBuilder.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ExternalProject.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(JavaProjectData.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(LanguageLevel.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(StdModuleTypes.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(JavaModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(EmptyModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ProjectImportAction.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(Init.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(org.slf4j.Logger.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(Log4jLoggerFactory.class));
    for (String entry : additionalEntries) {
      classPath.add(entry);
    }
  }

  @Override
  public void enhanceLocalProcessing(@NotNull List<URL> urls) {
  }

  /**
   * Stores information about given directories at the given content root
   *
   * @param contentRoot target paths info holder
   * @param type        type of data located at the given directories
   * @param dirs        directories which paths should be stored at the given content root
   * @throws IllegalArgumentException if specified by {@link ContentRootData#storePath(ExternalSystemSourceType, String)}
   */
  private static void populateContentRoot(@NotNull final ContentRootData contentRoot,
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
        // org.gradle.tooling.model.idea.IdeaSourceDirectory.isGenerated method supported only since Gradle 2.2
        LOG.warn(e.getMessage());
        printToolingProxyDiagnosticInfo(dir);
      }
      catch (Throwable e) {
        LOG.debug(e);
        printToolingProxyDiagnosticInfo(dir);
      }
      contentRoot.storePath(dirSourceType, dir.getDirectory().getAbsolutePath());
    }
  }

  private static void printToolingProxyDiagnosticInfo(@Nullable Object obj) {
    if (!LOG.isDebugEnabled() || obj == null) return;

    LOG.debug(String.format("obj: %s", obj));
    final Class<?> aClass = obj.getClass();
    LOG.debug(String.format("obj class: %s", aClass));
    LOG.debug(String.format("classloader: %s", aClass.getClassLoader()));
    for (Method m : aClass.getDeclaredMethods()) {
      LOG.debug(String.format("obj m: %s", m));
    }

    if (obj instanceof Proxy) {
      try {
        final Field hField = ReflectionUtil.findField(obj.getClass(), null, "h");
        hField.setAccessible(true);
        final Object h = hField.get(obj);
        final Field delegateField = ReflectionUtil.findField(h.getClass(), null, "delegate");
        delegateField.setAccessible(true);
        final Object delegate = delegateField.get(h);
        LOG.debug(String.format("delegate: %s", delegate));
        LOG.debug(String.format("delegate class: %s", delegate.getClass()));
        LOG.debug(String.format("delegate classloader: %s", delegate.getClass().getClassLoader()));
        for (Method m : delegate.getClass().getDeclaredMethods()) {
          LOG.debug(String.format("delegate m: %s", m));
        }
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        LOG.debug(e);
      }
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
                                                      @NotNull DataNode<ProjectData> ideProject)
    throws IllegalStateException {
    IdeaModule module = dependency.getDependencyModule();
    if (module == null) {
      if (resolverContext.getSettings() != null) {
        String moduleName = dependency.getTargetModuleName();
        GradleExecutionWorkspace executionWorkspace = resolverContext.getSettings().getExecutionWorkspace();
        ModuleData moduleData = executionWorkspace.findModuleDataByName(moduleName);
        if (moduleData != null) {
          return new ModuleDependencyData(ownerModule.getData(), moduleData);
        }
        else {
          for (IdeaProject project : resolverContext.getModels().getIncludedBuilds()) {
            moduleData = executionWorkspace.findModuleDataByName(project.getName()  + ':' + moduleName);
            if (moduleData != null) {
              return new ModuleDependencyData(ownerModule.getData(), moduleData);
            }
          }
        }
        return new ModuleDependencyData(
          ownerModule.getData(), new ModuleData("", GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), moduleName, "", ""));
      }
      throw new IllegalStateException(
        String.format("Can't parse gradle module dependency '%s'. Reason: referenced module is null", dependency)
      );
    }

    String moduleName = module.getName();
    if (moduleName == null) {
      throw new IllegalStateException(String.format(
        "Can't parse gradle module dependency '%s'. Reason: referenced module name is undefined (module: '%s') ", dependency, module
      ));
    }

    Set<String> registeredModuleNames = ContainerUtilRt.newHashSet();
    Collection<DataNode<ModuleData>> modulesDataNode = ExternalSystemApiUtil.getChildren(ideProject, ProjectKeys.MODULE);
    for (DataNode<ModuleData> moduleDataNode : modulesDataNode) {
      String name = moduleDataNode.getData().getExternalName();
      registeredModuleNames.add(name);
      if (name.equals(moduleName)) {
        return new ModuleDependencyData(ownerModule.getData(), moduleDataNode.getData());
      }
    }
    throw new IllegalStateException(String.format(
      "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. Registered modules: %s",
      dependency, moduleName, registeredModuleNames
    ));
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
    final GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();
    final LibraryLevel level;

    // Gradle API doesn't explicitly provide information about unresolved libraries (http://issues.gradle.org/browse/GRADLE-1995).
    // That's why we use this dirty hack here.
    boolean unresolved = binaryPath.getPath().startsWith(UNRESOLVED_DEPENDENCY_PREFIX);

    if (moduleVersion == null) {
      // use module library level if the dependency does not originate from a remote repository.
      level = LibraryLevel.MODULE;

      if (binaryPath.isFile()) {
        libraryName = FileUtil.getNameWithoutExtension(binaryPath);
      }
      else {
        libraryName = "";
      }

      if (unresolved) {
        // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
        libraryName = binaryPath.getPath().substring(UNRESOLVED_DEPENDENCY_PREFIX.length());
        int i = libraryName.indexOf(' ');
        if (i >= 0) {
          i = CharArrayUtil.shiftForward(libraryName, i + 1, " ");
        }

        if (i >= 0 && i < libraryName.length()) {
          int dependencyNameIndex = i;
          i = libraryName.indexOf(' ', dependencyNameIndex);
          if (i > 0) {
            libraryName = String.format("%s-%s", libraryName.substring(dependencyNameIndex, i), libraryName.substring(i + 1));
          }
        }
      }
    }
    else {
      level = LibraryLevel.PROJECT;
      libraryName = String.format("%s:%s:%s", moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
      if (binaryPath.isFile()) {
        String libraryFileName = FileUtil.getNameWithoutExtension(binaryPath);
        final String mavenLibraryFileName = String.format("%s-%s", moduleVersion.getName(), moduleVersion.getVersion());
        if (!mavenLibraryFileName.equals(libraryFileName)) {
          Pattern pattern = Pattern.compile(moduleVersion.getName() + "-" + moduleVersion.getVersion() + "-(.*)");
          Matcher matcher = pattern.matcher(libraryFileName);
          if (matcher.matches()) {
            final String classifier = matcher.group(1);
            libraryName += (":" + classifier);
          }
          else {
            final String artifactId = StringUtil.trimEnd(StringUtil.trimEnd(libraryFileName, moduleVersion.getVersion()), "-");
            libraryName = String.format("%s:%s:%s",
                                        moduleVersion.getGroup(),
                                        artifactId,
                                        moduleVersion.getVersion());
          }
        }
      }
    }

    // add packaging type to distinguish different artifact dependencies with same groupId:artifactId:version
    if (StringUtil.isNotEmpty(libraryName) && !FileUtilRt.extensionEquals(binaryPath.getPath(), "jar")) {
      libraryName += (":" + FileUtilRt.getExtension(binaryPath.getPath()));
    }

    final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, unresolved);
    if(moduleVersion != null) {
      library.setGroup(moduleVersion.getGroup());
      library.setArtifactId(moduleVersion.getName());
      library.setVersion(moduleVersion.getVersion());
    }

    if (!unresolved) {
      library.addPath(LibraryPathType.BINARY, binaryPath.getAbsolutePath());
    }

    File sourcePath = dependency.getSource();
    if (!unresolved && sourcePath != null) {
      library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
    }

    if (!unresolved && sourcePath == null) {
      attachGradleSdkSources(gradleModule, binaryPath, library, resolverCtx);
      if (resolverCtx instanceof DefaultProjectResolverContext) {
        attachSourcesAndJavadocFromGradleCacheIfNeeded(((DefaultProjectResolverContext)resolverCtx).getGradleUserHome(), library);
      }
    }

    File javadocPath = dependency.getJavadoc();
    if (!unresolved && javadocPath != null) {
      library.addPath(LibraryPathType.DOC, javadocPath.getAbsolutePath());
    }

    if (level == LibraryLevel.PROJECT) {
      linkProjectLibrary(ideProject, library);
    }

    return new LibraryDependencyData(ownerModule.getData(), library, level);
  }

  private interface SourceSetsProcessor {
    void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet);
  }
}
