/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.builder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleDependencyScope;
import org.jetbrains.plugins.gradle.model.ModelBuilderService;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;
import org.jetbrains.plugins.gradle.model.internal.*;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModelDependenciesBuilderImpl implements ModelBuilderService {

  private static final String MODULE_PROPERTY = "ideaModule";
  private static final String VERSION_PROPERTY = "version";
  private static final String GROUP_PROPERTY = "group";

  @Override
  public boolean canBuild(String modelName) {
    return ProjectDependenciesModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(final String modelName, final Project project) {
    final List<IdeaDependency> dependencies = new ArrayList<IdeaDependency>();

    final Map<DependencyVersionId, Scopes> scopesMap = new LinkedHashMap<DependencyVersionId, Scopes>();
    final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor();

    boolean offline = false;
    boolean downloadJavadoc = false;
    boolean downloadSources = true;

    final IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);
    Map<String, Map<String, Collection<Configuration>>> userScopes = Collections.emptyMap();
    if (ideaPlugin != null) {
      IdeaModel ideaModel = ideaPlugin.getModel();
      if (ideaModel != null && ideaModel.getModule() != null) {
        offline = ideaModel.getModule().isOffline();
        downloadJavadoc = ideaModel.getModule().isDownloadJavadoc();
        downloadSources = ideaModel.getModule().isDownloadSources();
        userScopes = ideaModel.getModule().getScopes();
      }
    }

    for (final Configuration configuration : project.getConfigurations()) {
      Collection<Configuration> plusConfigurations = new ArrayList<Configuration>();
      plusConfigurations.add(configuration);

      final List<IdeDependenciesExtractor.IdeProjectDependency> ideProjectDependencies =
        dependenciesExtractor.extractProjectDependencies(plusConfigurations, new ArrayList<Configuration>());

      for (IdeDependenciesExtractor.IdeProjectDependency ideProjectDependency : ideProjectDependencies) {
        merge(scopesMap, ideProjectDependency, userScopes);
      }

      if (!offline) {
        final Collection<IdeDependenciesExtractor.IdeRepoFileDependency> ideRepoFileDependencies =
          dependenciesExtractor.extractRepoFileDependencies(
            project.getConfigurations(), plusConfigurations, new ArrayList<Configuration>(), downloadSources, downloadJavadoc);
        for (IdeDependenciesExtractor.IdeRepoFileDependency repoFileDependency : ideRepoFileDependencies) {
          merge(scopesMap, repoFileDependency, userScopes);
        }
      }

      final List<IdeDependenciesExtractor.IdeLocalFileDependency> ideLocalFileDependencies =
        dependenciesExtractor.extractLocalFileDependencies(plusConfigurations, new ArrayList<Configuration>());
      for (IdeDependenciesExtractor.IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
        merge(scopesMap, fileDependency, userScopes);
      }
    }

    for (Map.Entry<DependencyVersionId, Scopes> entry : scopesMap.entrySet()) {
      DependencyVersionId versionId = entry.getKey();
      for (GradleDependencyScope scope : entry.getValue().getScopes()) {
        if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeRepoFileDependency) {
          IdeDependenciesExtractor.IdeRepoFileDependency repoFileDependency =
            (IdeDependenciesExtractor.IdeRepoFileDependency)versionId.getIdeDependency();
          IdeaSingleEntryLibraryDependencyImpl libraryDependency = new IdeaSingleEntryLibraryDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion(),
            versionId.getClassifier()
          );
          libraryDependency.setFile(repoFileDependency.getFile());
          libraryDependency.setSource(repoFileDependency.getSourceFile());
          libraryDependency.setJavadoc(repoFileDependency.getJavadocFile());
          dependencies.add(libraryDependency);
        }
        else if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeProjectDependency) {
          IdeDependenciesExtractor.IdeProjectDependency projectDependency =
            (IdeDependenciesExtractor.IdeProjectDependency)versionId.getIdeDependency();

          String ideaModuleName = findDeDuplicatedModuleName(projectDependency.getProject());
          if (ideaModuleName == null) {
            ideaModuleName = versionId.getName();
          }

          IdeaModuleDependencyImpl moduleDependency = new IdeaModuleDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            ideaModuleName,
            versionId.getGroup(),
            versionId.getVersion(),
            versionId.getClassifier()
          );
          moduleDependency.setIdeaModule(new StubIdeaModule(ideaModuleName));
          dependencies.add(moduleDependency);
        }
        else if (versionId.getIdeDependency() instanceof IdeDependenciesExtractor.IdeLocalFileDependency) {
          IdeDependenciesExtractor.IdeLocalFileDependency fileDependency =
            (IdeDependenciesExtractor.IdeLocalFileDependency)versionId.getIdeDependency();
          IdeaSingleEntryLibraryDependencyImpl libraryDependency = new IdeaSingleEntryLibraryDependencyImpl(
            new IdeaDependencyScopeImpl(scope),
            versionId.getName(),
            versionId.getGroup(),
            versionId.getVersion(),
            versionId.getClassifier()
          );
          libraryDependency.setFile(fileDependency.getFile());
          attachGradleSdkSources(libraryDependency, fileDependency);
          dependencies.add(libraryDependency);
        }
      }
    }

    return new ProjectDependenciesModelImpl(project.getPath(), dependencies);
  }

  private static void attachGradleSdkSources(IdeaSingleEntryLibraryDependencyImpl libraryDependency,
                                             IdeDependenciesExtractor.IdeLocalFileDependency localFileDependency) {
    final String libName = localFileDependency.getFile().getName();
    if (localFileDependency.getFile() == null || !libName.startsWith("gradle-")) return;

    File libOrPluginsFile = localFileDependency.getFile().getParentFile();
    if (libOrPluginsFile != null && ("plugins".equals(libOrPluginsFile.getName()))) {
      libOrPluginsFile = libOrPluginsFile.getParentFile();
    }

    if (libOrPluginsFile != null && "lib".equals(libOrPluginsFile.getName()) && libOrPluginsFile.getParentFile() != null) {
      File srcDir = new File(libOrPluginsFile.getParentFile(), "src");
      if (GradleVersion.current().compareTo(GradleVersion.version("1.9")) >= 0) {
        int endIndex = libName.indexOf(GradleVersion.current().getVersion() + ".jar");
        if (endIndex != -1) {
          String srcDirChild = libName.substring("gradle-".length(), endIndex - 1);
          srcDir = new File(srcDir, srcDirChild);
        }
      }

      if (srcDir.isDirectory()) {
        libraryDependency.setSource(srcDir);
      }
    }
  }

  @Nullable
  private static String findDeDuplicatedModuleName(Project project) {
    if (project.hasProperty(MODULE_PROPERTY)) {
      Object ideaModule = project.property(MODULE_PROPERTY);
      if (ideaModule instanceof GenerateIdeaModule) {
        GenerateIdeaModule generateIdeaModule = (GenerateIdeaModule)ideaModule;
        return generateIdeaModule.getModule().getName();
      }
    }
    return null;
  }

  private static void merge(Map<DependencyVersionId, Scopes> map,
                            IdeDependenciesExtractor.IdeProjectDependency dependency,
                            Map<String, Map<String, Collection<Configuration>>> userScopes) {
    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = deduceScope(configurationName, userScopes);
    if (scope == null) return;

    final Project project = dependency.getProject();
    final String version = project.hasProperty(VERSION_PROPERTY) ? str(project.property(VERSION_PROPERTY)) : "";
    final String group = project.hasProperty(GROUP_PROPERTY) ? str(project.property(GROUP_PROPERTY)) : "";

    DependencyVersionId versionId =
      new DependencyVersionId(dependency, project.getName(), group, version, null);
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  private static String str(Object o) {
    return String.valueOf(o == null ? "" : o);
  }

  private static void merge(Map<DependencyVersionId, Scopes> map,
                            IdeDependenciesExtractor.IdeRepoFileDependency dependency,
                            Map<String, Map<String, Collection<Configuration>>> userScopes) {
    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = deduceScope(configurationName, userScopes);
    if (scope == null) return;

    final ModuleVersionIdentifier dependencyId;
    if (dependency instanceof IdeDependenciesExtractor.UnresolvedIdeRepoFileDependency) {
      IdeDependenciesExtractor.UnresolvedIdeRepoFileDependency unresolvedDependency =
        (IdeDependenciesExtractor.UnresolvedIdeRepoFileDependency)dependency;
      dependencyId = new MyModuleVersionIdentifier(unresolvedDependency.getFile().getName());
    }
    else {
      dependencyId = dependency.getId();
    }

    String classifier = parseClassifier(dependencyId, dependency.getFile());
    DependencyVersionId versionId =
      new DependencyVersionId(dependency, dependencyId.getName(), dependencyId.getGroup(), dependencyId.getVersion(), classifier);
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  private static String parseClassifier(ModuleVersionIdentifier dependencyId, File dependencyFile) {
    if (dependencyFile == null) return null;
    String dependencyFileName = dependencyFile.getName();
    int i = dependencyFileName.indexOf(dependencyId.getName() + '-' + dependencyId.getVersion() + '-');
    return i != -1 ? dependencyFileName.substring(i, dependencyFileName.length()) : null;
  }

  private static void merge(Map<DependencyVersionId, Scopes> map,
                            IdeDependenciesExtractor.IdeLocalFileDependency dependency,
                            Map<String, Map<String, Collection<Configuration>>> userScopes) {

    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = deduceScope(configurationName, userScopes);
    if (scope == null) return;

    String path = dependency.getFile().getPath();
    DependencyVersionId versionId =
      new DependencyVersionId(dependency, path, "", "", null);
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  /**
   * Deduce configuration scope based on configuration name using gradle conventions.
   * IDEA gradle plugin only 'plus' configuration used to support configuration based on a custom configuration (not conventional)
   *
   * @param configurationName gradle configuration name
   * @param userScopes        gradle IDEA plugin scopes map
   * @return deduced scope
   */
  private static GradleDependencyScope deduceScope(String configurationName,
                                                   Map<String, Map<String, Collection<Configuration>>> userScopes) {
    GradleDependencyScope scope = GradleDependencyScope.fromName(configurationName);
    for (Map.Entry<String, Map<String, Collection<Configuration>>> entry : userScopes.entrySet()) {
      Collection<Configuration> plusConfigurations = entry.getValue().get("plus");
      if (plusConfigurations == null) continue;

      for (Configuration plus : plusConfigurations) {
        if (plus.getName().equals(configurationName)) {
          return GradleDependencyScope.fromIdeaMappingName(entry.getKey().toLowerCase());
        }
      }
    }

    return scope;
  }

  private static class MyModuleVersionIdentifier implements ModuleVersionIdentifier, Serializable {
    private final String myName;

    public MyModuleVersionIdentifier(String name) {

      myName = name;
    }

    @Override
    public String getVersion() {
      return null;
    }

    @Override
    public String getGroup() {
      return null;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public ModuleIdentifier getModule() {
      return null;
    }
  }
}
