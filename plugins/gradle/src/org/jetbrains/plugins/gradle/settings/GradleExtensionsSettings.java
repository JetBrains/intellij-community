/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.model.ExternalTask;
import org.jetbrains.plugins.gradle.model.GradleExtensions;
import org.jetbrains.plugins.gradle.model.GradleProperty;
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
@State(name = "GradleExtensions", storages = {@Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)})
public class GradleExtensionsSettings implements PersistentStateComponent<GradleExtensionsSettings.Settings> {

  private static final Logger LOG = Logger.getInstance(GradleExtensionsSettings.class);
  private final Settings myState = new Settings();

  public GradleExtensionsSettings(Project project) {
    ExternalSystemApiUtil.subscribe(project, GradleConstants.SYSTEM_ID, new GradleSettingsListenerAdapter() {
      @Override
      public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
        myState.remove(linkedProjectPaths);
      }
    });
  }

  @Nullable
  @Override
  public Settings getState() {
    // cleanup GradleExtensions entries created by previous version from workspace file
    // TODO remove PersistentStateComponent implementation in future version
    return new Settings();
  }

  @Override
  public void loadState(Settings state) {
  }

  @NotNull
  public static Settings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleExtensionsSettings.class).myState;
  }

  public static void load(Project project) {
    final Collection<ExternalProjectInfo> projectsData =
      ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID);
    for (ExternalProjectInfo projectInfo : projectsData) {
      DataNode<ProjectData> projectDataNode = projectInfo.getExternalProjectStructure();
      if (projectDataNode == null) continue;

      Collection<DataNode<GradleExtensions>> nodes = new SmartList<>();
      for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)) {
        ContainerUtil.addIfNotNull(nodes, ExternalSystemApiUtil.find(moduleNode, GradleExtensionsDataService.KEY));
      }
      getInstance(project).add(projectInfo.getExternalProjectPath(), nodes);
    }
  }

  public static class Settings {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "project", keyAttributeName = "path")
    @NotNull
    public Map<String, GradleProject> projects = new HashMap<>();

    public void add(@NotNull String rootPath,
                    @NotNull Collection<DataNode<GradleExtensions>> extensionsData) {
      Map<String, GradleExtensions> extensionMap = ContainerUtil.newHashMap();
      for (DataNode<GradleExtensions> node : extensionsData) {
        DataNode<?> parent = node.getParent();
        if (parent == null) continue;
        if (!(parent.getData() instanceof ModuleData)) continue;
        String projectPath = ((ModuleData)parent.getData()).getLinkedExternalProjectPath();
        extensionMap.put(projectPath, node.getData());
      }

      add(rootPath, extensionMap);
    }

    public void add(@NotNull String rootPath, @NotNull Map<String, GradleExtensions> extensions) {
      GradleProject gradleProject = new GradleProject();
      for (Map.Entry<String, GradleExtensions> entry : extensions.entrySet()) {
        GradleExtensionsData extensionsData = new GradleExtensionsData();
        GradleExtensions gradleExtensions = entry.getValue();
        try {
          File parentProjectDir = gradleExtensions.getParentProjectDir();
          if (parentProjectDir != null) {
            extensionsData.parent = ExternalSystemApiUtil.toCanonicalPath(parentProjectDir.getCanonicalPath());
          }
        }
        catch (IOException e) {
          LOG.warn("construction of the canonical path for the gradle project fails", e);
        }
        for (org.jetbrains.plugins.gradle.model.GradleExtension extension : gradleExtensions.getExtensions()) {
          GradleExtension gradleExtension = new GradleExtension();
          gradleExtension.name = extension.getName();
          gradleExtension.rootTypeFqn = extension.getTypeFqn();
          gradleExtension.namedObjectTypeFqn = extension.getNamedObjectTypeFqn();
          extensionsData.extensions.add(gradleExtension);
        }
        for (GradleProperty property : gradleExtensions.getGradleProperties()) {
          GradleProp gradleProp = new GradleProp();
          gradleProp.name = property.getName();
          gradleProp.typeFqn = property.getTypeFqn();
          extensionsData.properties.add(gradleProp);
        }
        for (ExternalTask task : gradleExtensions.getTasks()) {
          GradleTask gradleTask = new GradleTask();
          gradleTask.name = task.getName();
          String type = task.getType();
          if (type != null) {
            gradleTask.typeFqn = type;
          }

          StringBuilder description = new StringBuilder();
          if (task.getDescription() != null) {
            description.append(task.getDescription());
            if (task.getGroup() != null) {
              description.append("<p>");
            }
          }
          if (task.getGroup() != null) {
            description.append("<i>Task group: ").append(task.getGroup()).append("<i>");
          }

          gradleTask.description = description.toString();
          extensionsData.tasks.add(gradleTask);
        }
        for (org.jetbrains.plugins.gradle.model.GradleConfiguration configuration : gradleExtensions.getConfigurations()) {
          GradleConfiguration gradleConfiguration = new GradleConfiguration();
          gradleConfiguration.name = configuration.getName();
          gradleConfiguration.description = configuration.getDescription();
          gradleConfiguration.visible = configuration.isVisible();
          gradleConfiguration.scriptClasspath = configuration.isScriptClasspathConfiguration();
          extensionsData.configurations.add(gradleConfiguration);
        }
        gradleProject.extensions.put(entry.getKey(), extensionsData);
        extensionsData.myGradleProject = gradleProject;
      }

      Map<String, GradleProject> projects = new HashMap<>(this.projects);
      projects.put(rootPath, gradleProject);
      this.projects = projects;
    }

    public void remove(Set<String> rootPaths) {
      Map<String, GradleProject> projects = new HashMap<>(this.projects);
      for (String path : rootPaths) {
        projects.remove(path);
      }
      this.projects = projects;
    }

    @Nullable
    public GradleExtensionsData getExtensionsFor(@Nullable Module module) {
      if (module == null) return null;
      return getExtensionsFor(ExternalSystemApiUtil.getExternalRootProjectPath(module),
                              ExternalSystemApiUtil.getExternalProjectPath(module));
    }

    @Nullable
    public GradleExtensionsData getExtensionsFor(@Nullable String rootProjectPath, @Nullable String projectPath) {
      if (rootProjectPath == null || projectPath == null) return null;
      GradleProject gradleProject = projects.get(rootProjectPath);
      if (gradleProject == null) return null;
      return gradleProject.extensions.get(projectPath);
    }
  }

  @Tag("sub-project")
  static class GradleProject {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "project", keyAttributeName = "path")
    @NotNull
    public Map<String, GradleExtensionsData> extensions = new HashMap<>();
  }


  @Tag("extensions")
  public static class GradleExtensionsData {
    public GradleExtensionsData() {
    }

    @Transient
    private GradleProject myGradleProject;

    @Attribute("parent")
    public String parent;
    @Property(surroundWithTag = false)
    @XCollection
    public List<GradleExtension> extensions = new SmartList<>();
    @Property(surroundWithTag = false)
    @XCollection
    public List<GradleProp> properties = new SmartList<>();
    @Property(surroundWithTag = false)
    @XCollection
    public List<GradleTask> tasks = new SmartList<>();
    @Property(surroundWithTag = false)
    @XCollection
    public List<GradleConfiguration> configurations = new SmartList<>();

    @Transient
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
      return findAllProperties(this, ContainerUtil.newHashMap());
    }

    @NotNull
    private static Collection<GradleProp> findAllProperties(@NotNull GradleExtensionsData extensionsData,
                                                            @NotNull Map<String, GradleProp> result) {
      for (GradleProp property : extensionsData.properties) {
        if (result.containsKey(property.name)) continue;
        result.put(property.name, property);
      }
      if (extensionsData.getParent() != null) {
        findAllProperties(extensionsData.getParent(), result);
      }
      return result.values();
    }

    @Nullable
    private static GradleProp findProperty(@NotNull GradleExtensionsData extensionsData, String propName) {
      for (GradleProp property : extensionsData.properties) {
        if (property.name.equals(propName)) return property;
      }
      if (extensionsData.parent != null && extensionsData.myGradleProject != null) {
        GradleExtensionsData parentData = extensionsData.myGradleProject.extensions.get(extensionsData.parent);
        if (parentData != null) {
          return findProperty(parentData, propName);
        }
      }
      return null;
    }
  }

  public interface TypeAware {
    String getTypeFqn();
  }

  @Tag("ext")
  public static class GradleExtension implements TypeAware {
    @Attribute("name")
    public String name;
    @Attribute("type")
    public String rootTypeFqn = CommonClassNames.JAVA_LANG_OBJECT_SHORT;
    @Attribute("objectType")
    public String namedObjectTypeFqn;

    @Override
    public String getTypeFqn() {
      return rootTypeFqn;
    }
  }

  @Tag("prop")
  public static class GradleProp implements TypeAware {
    @Attribute("name")
    public String name;
    @Attribute("type")
    public String typeFqn = CommonClassNames.JAVA_LANG_STRING;
    @Nullable
    @Text
    public String value;

    @Override
    public String getTypeFqn() {
      return typeFqn;
    }
  }

  @Tag("task")
  public static class GradleTask implements TypeAware {
    @Attribute("name")
    public String name;
    @Attribute("type")
    public String typeFqn = GradleCommonClassNames.GRADLE_API_DEFAULT_TASK;
    @Nullable
    @Text
    public String description;

    @Override
    public String getTypeFqn() {
      return typeFqn;
    }
  }

  @Tag("conf")
  public static class GradleConfiguration {
    @Attribute("name")
    public String name;
    @Attribute("visible")
    public boolean visible = true;
    @Attribute("scriptClasspath")
    public boolean scriptClasspath;
    @Text
    public String description;
  }
}
