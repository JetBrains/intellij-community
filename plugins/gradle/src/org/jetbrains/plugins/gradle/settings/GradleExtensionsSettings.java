// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;

import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull;
import static org.jetbrains.plugins.gradle.util.GradleModuleDataKt.getGradleIdentityPath;

/**
 * @author Vladislav.Soroka
 */
public class GradleExtensionsSettings {

  private static final Logger LOG = Logger.getInstance(GradleExtensionsSettings.class);
  private final Settings myState = new Settings();

  public GradleExtensionsSettings(Project project) {
    ExternalSystemApiUtil.subscribe(project, GradleConstants.SYSTEM_ID, new GradleSettingsListener() {
      @Override
      public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
        myState.remove(linkedProjectPaths);
      }
    });
  }

  @NotNull
  public static Settings getInstance(@NotNull Project project) {
    return project.getService(GradleExtensionsSettings.class).myState;
  }

  public static void load(Project project) {
    final Collection<ExternalProjectInfo> projectsData =
      ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID);
    for (ExternalProjectInfo projectInfo : projectsData) {
      DataNode<ProjectData> projectDataNode = projectInfo.getExternalProjectStructure();
      if (projectDataNode == null) continue;

      String projectPath = projectInfo.getExternalProjectPath();
      try {
        Collection<DataNode<GradleExtensions>> nodes = new SmartList<>();
        for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)) {
          ContainerUtil.addIfNotNull(nodes, ExternalSystemApiUtil.find(moduleNode, GradleExtensionsDataService.KEY));
        }
        getInstance(project).add(projectPath, nodes);
      }
      catch (ClassCastException e) {
        // catch deserialization issue caused by fast serializer
        LOG.debug(e);
        ExternalProjectsManager.getInstance(project).getExternalProjectsWatcher().markDirty(projectPath);
      }
    }
  }

  public static class Settings {
    private final @NotNull Map<String, GradleProject> projects = new HashMap<>();

    public void add(@NotNull String rootPath,
                    @NotNull Collection<? extends DataNode<GradleExtensions>> extensionsData) {
      Map<String, GradleExtensions> extensionMap = new HashMap<>();
      for (DataNode<GradleExtensions> node : extensionsData) {
        DataNode<?> parent = node.getParent();
        if (parent == null) continue;
        if (!(parent.getData() instanceof ModuleData)) continue;
        String gradlePath = getGradleIdentityPath((ModuleData)parent.getData());
        extensionMap.put(gradlePath, node.getData());
      }

      add(rootPath, extensionMap);
    }

    public void add(@NotNull String rootPath, @NotNull Map<String, GradleExtensions> extensions) {
      GradleProject gradleProject = new GradleProject();
      GradleExtensionDataFactory factory = new GradleExtensionDataFactory();
      for (Map.Entry<String, GradleExtensions> entry : extensions.entrySet()) {
        GradleExtensionsData extensionsData = factory.getGradleExtensionsData(entry.getValue(), gradleProject);
        gradleProject.extensions.put(entry.getKey(), extensionsData);
      }
      projects.put(rootPath, gradleProject);
    }

    public void remove(@NotNull Set<String> rootPaths) {
      for (String path : rootPaths) {
        projects.remove(path);
      }
    }

    /**
     * Returns extensions available in the context of the gradle project related to the IDE module.
     */
    @Nullable
    public GradleExtensionsData getExtensionsFor(@Nullable Module module) {
      if (module == null) return null;
      return getExtensionsFor(ExternalSystemApiUtil.getExternalRootProjectPath(module),
                              getGradleIdentityPathOrNull(module));
    }

    /**
     * Returns extensions available in the context of the specified (using gradle path notation, e.g. `:sub-project`) gradle project.
     *
     * @param rootProjectPath file path of the root gradle project
     * @param gradlePath      gradle project path notation
     * @return gradle extensions
     */
    @Nullable
    public GradleExtensionsData getExtensionsFor(@Nullable String rootProjectPath, @Nullable String gradlePath) {
      GradleProject gradleProject = getRootGradleProject(rootProjectPath);
      if (gradleProject == null) return null;
      return gradleProject.extensions.get(gradlePath);
    }

    @Contract("null -> null")
    @Nullable
    public GradleProject getRootGradleProject(@Nullable String rootProjectPath) {
      if (rootProjectPath == null) return null;
      return projects.get(rootProjectPath);
    }
  }

  public static class GradleProject {
    public Map<String, GradleExtensionsData> extensions = new HashMap<>();
  }

  public static class GradleExtensionsData {

    private final @Nullable GradleProject myGradleProject;
    private final @Nullable String parent;

    public final @NotNull Map<String, GradleExtension> extensions;
    public final @NotNull List<GradleConvention> conventions;
    public final @NotNull Map<String, GradleProp> properties;
    public final @NotNull Map<String, GradleTask> tasksMap;
    public final @NotNull Map<String, GradleConfiguration> configurations;
    public final @NotNull Map<String, GradleConfiguration> buildScriptConfigurations;

    @VisibleForTesting
    @ApiStatus.Internal
    public GradleExtensionsData(@Nullable GradleProject gradleProject,
                                @Nullable String parent,
                                @NotNull Map<String, GradleExtension> extensions,
                                @NotNull List<GradleConvention> conventions,
                                @NotNull Map<String, GradleProp> properties,
                                @NotNull Map<String, GradleTask> tasksMap,
                                @NotNull Map<String, GradleConfiguration> configurations,
                                @NotNull Map<String, GradleConfiguration> buildScriptConfigurations) {
      this.myGradleProject = gradleProject;
      this.parent = parent;
      this.extensions = Collections.unmodifiableMap(extensions);
      this.conventions = Collections.unmodifiableList(conventions);
      this.properties = Collections.unmodifiableMap(properties);
      this.tasksMap = Collections.unmodifiableMap(tasksMap);
      this.configurations = Collections.unmodifiableMap(configurations);
      this.buildScriptConfigurations = Collections.unmodifiableMap(buildScriptConfigurations);
    }

    @Nullable
    public GradleExtensionsData getParent() {
      if (myGradleProject == null) return null;
      return myGradleProject.extensions.get(parent);
    }

    @Nullable
    public GradleProp findProperty(@Nullable String name) {
      return findProperty(this, name);
    }

    @NotNull
    public Collection<GradleProp> findAllProperties() {
      return findAllProperties(this, new HashMap<>());
    }

    @NotNull
    private static Collection<GradleProp> findAllProperties(@NotNull GradleExtensionsData extensionsData,
                                                            @NotNull Map<String, GradleProp> result) {
      for (GradleProp property : extensionsData.properties.values()) {
        result.putIfAbsent(property.name, property);
      }
      if (extensionsData.getParent() != null) {
        findAllProperties(extensionsData.getParent(), result);
      }
      return result.values();
    }

    @Nullable
    private static GradleProp findProperty(@NotNull GradleExtensionsData extensionsData, String propName) {
      GradleProp prop = extensionsData.properties.get(propName);
      if (prop != null) return prop;
      if (extensionsData.parent != null && extensionsData.myGradleProject != null) {
        GradleExtensionsData parentData = extensionsData.myGradleProject.extensions.get(extensionsData.parent);
        if (parentData != null) {
          return findProperty(parentData, propName);
        }
      }
      return null;
    }
  }

  private static class GradleExtensionDataFactory {

    private final @NotNull Map<DefaultGradleExtension, GradleExtension> extensionCache = new HashMap<>();
    private final @NotNull Map<DefaultGradleConvention, GradleConvention> conventionCache = new HashMap<>();
    private final @NotNull Map<DefaultGradleProperty, GradleProp> propertyCache = new HashMap<>();
    // Task name + fqdn -> GradleTask.
    // We're not interested in the task description because it will lead to a cache miss in the majority of cases.
    private final @NotNull Map<String, GradleTask> taskCache = new HashMap<>();
    private final @NotNull Map<DefaultGradleConfiguration, GradleConfiguration> configurationCache = new HashMap<>();

    /**
     * It is safe to cache GradleExtensionsData.
     * All the extension data objects are parts of the same GradleProject.
     */
    private final @NotNull Map<DefaultGradleExtensions, GradleExtensionsData> gradleExtensionsCache = new HashMap<>();

    public @NotNull GradleExtensionsData getGradleExtensionsData(@NotNull GradleExtensions gradleExtensions,
                                                                 @NotNull GradleProject gradleProject) {
      return gradleExtensionsCache.computeIfAbsent(
        (DefaultGradleExtensions)gradleExtensions,
        e -> convertGradleExtensionsData(e, gradleProject)
      );
    }

    private @NotNull GradleExtensionsData convertGradleExtensionsData(@NotNull GradleExtensions gradleExtensions,
                                                                      @NotNull GradleProject gradleProject) {
      Map<String, GradleExtension> extensions = new HashMap<>();
      for (org.jetbrains.plugins.gradle.model.GradleExtension extension : gradleExtensions.getExtensions()) {
        GradleExtension gradleExtension = extensionCache.computeIfAbsent((DefaultGradleExtension)extension,
                                                                         GradleExtensionDataFactory::convertGradleExtension);
        extensions.put(gradleExtension.getName(), gradleExtension);
      }

      List<GradleConvention> conventions = new SmartList<>();
      for (org.jetbrains.plugins.gradle.model.GradleConvention convention : gradleExtensions.getConventions()) {
        GradleConvention gradleConvention = conventionCache.computeIfAbsent((DefaultGradleConvention)convention,
                                                                            GradleExtensionDataFactory::convertGradleConvention);
        conventions.add(gradleConvention);
      }

      Map<String, GradleProp> properties = new HashMap<>();
      for (GradleProperty property : gradleExtensions.getGradleProperties()) {
        GradleProp gradleProp = propertyCache.computeIfAbsent((DefaultGradleProperty)property,
                                                              GradleExtensionDataFactory::convertGradleProp);
        properties.put(gradleProp.getName(), gradleProp);
      }

      Map<String, GradleTask> tasksMap = new LinkedHashMap<>();
      for (ExternalTask task : gradleExtensions.getTasks()) {
        GradleTask gradleTask = taskCache.computeIfAbsent(task.getName() + task.getType(), __ -> convertGradleTask(task));
        tasksMap.put(gradleTask.getName(), gradleTask);
      }

      Map<String, GradleConfiguration> configurations = new HashMap<>();
      Map<String, GradleConfiguration> buildScriptConfigurations = new HashMap<>();
      for (org.jetbrains.plugins.gradle.model.GradleConfiguration configuration : gradleExtensions.getConfigurations()) {
        GradleConfiguration gradleConfiguration = configurationCache.computeIfAbsent((DefaultGradleConfiguration)configuration,
                                                                                     GradleExtensionDataFactory::convertGradleConfiguration);
        if (gradleConfiguration.scriptClasspath) {
          buildScriptConfigurations.put(gradleConfiguration.getName(), gradleConfiguration);
        }
        else {
          configurations.put(gradleConfiguration.getName(), gradleConfiguration);
        }
      }

      return new GradleExtensionsData(gradleProject, gradleExtensions.getParentProjectPath(),
                                      extensions, conventions,
                                      properties, tasksMap,
                                      configurations, buildScriptConfigurations
      );
    }

    private static @NotNull GradleExtension convertGradleExtension(@NotNull org.jetbrains.plugins.gradle.model.GradleExtension extension) {
      return new GradleExtension(
        extension.getName(),
        extension.getTypeFqn()
      );
    }

    private static @NotNull GradleConvention convertGradleConvention(@NotNull org.jetbrains.plugins.gradle.model.GradleConvention convention) {
      return new GradleConvention(
        convention.getName(),
        convention.getTypeFqn()
      );
    }

    private static @NotNull GradleProp convertGradleProp(@NotNull GradleProperty property) {
      return new GradleProp(
        property.getName(),
        property.getTypeFqn()
      );
    }

    private static @NotNull GradleTask convertGradleTask(@NotNull ExternalTask task) {
      return new GradleTask(
        task.getName(),
        Objects.requireNonNullElse(task.getType(), GradleCommonClassNames.GRADLE_API_DEFAULT_TASK)
      );
    }

    private static @NotNull GradleConfiguration convertGradleConfiguration(
      @NotNull org.jetbrains.plugins.gradle.model.GradleConfiguration configuration
    ) {
      return new GradleConfiguration(
        configuration.getName(),
        configuration.isVisible(),
        configuration.isScriptClasspathConfiguration(),
        configuration.getDescription(),
        configuration.getDeclarationAlternatives()
      );
    }
  }

  public interface TypeAware {
    String getTypeFqn();
  }

  public static class GradleExtension implements TypeAware {
    private final @NotNull String name;
    private final @NotNull String typeFqn;

    public GradleExtension(@NotNull String name, @NotNull String typeFqn) {
      this.name = name;
      this.typeFqn = typeFqn;
    }

    public @NotNull String getName() {
      return name;
    }

    @Override
    public @NotNull String getTypeFqn() {
      return typeFqn;
    }
  }

  public static class GradleConvention implements TypeAware {
    private final @NotNull String name;
    private final @NotNull String typeFqn;

    public GradleConvention(@NotNull String name, @NotNull String typeFqn) {
      this.name = name;
      this.typeFqn = typeFqn;
    }

    public @NotNull String getName() {
      return name;
    }

    @Override
    public @NotNull String getTypeFqn() {
      return typeFqn;
    }
  }

  public static class GradleProp implements TypeAware {
    private final @NotNull String name;
    private final @NotNull String typeFqn;

    public GradleProp(@NotNull String name, @NotNull String typeFqn) {
      this.name = name;
      this.typeFqn = typeFqn;
    }

    public @NotNull String getName() {
      return name;
    }

    @Override
    public @NotNull String getTypeFqn() {
      return typeFqn;
    }
  }

  public static class GradleTask implements TypeAware {

    /**
     * Do not access the field directly, use {@link GradleTask#getName()} instead.
     */
    @ApiStatus.Internal
    public final @NotNull String name;

    /**
     * Do not access the field directly, use {@link GradleTask#getTypeFqn()} instead.
     */
    @ApiStatus.Internal
    public final @NotNull String typeFqn;

    public GradleTask(@NotNull String name, @NotNull String typeFqn) {
      this.name = name;
      this.typeFqn = typeFqn;
    }

    public @NotNull String getName() {
      return name;
    }

    @Override
    public @NotNull String getTypeFqn() {
      return typeFqn;
    }
  }

  public static class GradleConfiguration {
    private final @NotNull String name;
    private final boolean visible;
    private final boolean scriptClasspath;
    private final @Nullable String description;
    private final @NotNull List<String> declarationAlternatives;

    public GradleConfiguration(@NotNull String name,
                               boolean visible,
                               boolean scriptClasspath,
                               @Nullable String description,
                               @NotNull List<String> declarationAlternatives) {
      this.name = name;
      this.visible = visible;
      this.scriptClasspath = scriptClasspath;
      this.description = description;
      this.declarationAlternatives = declarationAlternatives;
    }

    public @NotNull String getName() {
      return name;
    }

    public boolean isVisible() {
      return visible;
    }

    public boolean isScriptClasspath() {
      return scriptClasspath;
    }

    public @Nullable String getDescription() {
      return description;
    }

    public @NotNull List<String> getDeclarationAlternatives() {
      return declarationAlternatives;
    }
  }

  @Nullable
  public static GradleProject getRootProject(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile().getOriginalFile();
    final Project project = containingFile.getProject();
    return getInstance(project).getRootGradleProject(getRootProjectPath(element));
  }

  @Nullable
  public static String getRootProjectPath(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile().getOriginalFile();
    final Module module = ModuleUtilCore.findModuleForFile(containingFile);
    return ExternalSystemApiUtil.getExternalRootProjectPath(module);
  }
}
