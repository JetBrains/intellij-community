/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.v1_11.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleDependencyScope;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaSingleEntryLibraryDependencyImpl;
import org.jetbrains.plugins.gradle.tooling.internal.Scopes;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 2/3/14
 */
public class ModelDependenciesBuilderImplHelper {

  private static final String MODULE_PROPERTY = "ideaModule";
  private static final String VERSION_PROPERTY = "version";
  private static final String GROUP_PROPERTY = "group";


  public static void attachGradleSdkSources(IdeaSingleEntryLibraryDependencyImpl libraryDependency,
                                            IdeLocalFileDependency localFileDependency) {
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
  public static String findDeDuplicatedModuleName(Project project) {
    if (project.hasProperty(MODULE_PROPERTY)) {
      Object ideaModule = project.property(MODULE_PROPERTY);
      if (ideaModule instanceof GenerateIdeaModule) {
        GenerateIdeaModule generateIdeaModule = (GenerateIdeaModule)ideaModule;
        return generateIdeaModule.getModule().getName();
      }
    }
    return null;
  }

  public static void merge(Map<InternalDependencyVersionId, Scopes> map,
                           IdeProjectDependency dependency,
                           Map<String, Map<String, Collection<Configuration>>> userScopes) {
    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = deduceScope(configurationName, userScopes);
    if (scope == null) return;

    final Project project = dependency.getProject();
    final String version = project.hasProperty(VERSION_PROPERTY) ? str(project.property(VERSION_PROPERTY)) : "";
    final String group = project.hasProperty(GROUP_PROPERTY) ? str(project.property(GROUP_PROPERTY)) : "";

    InternalDependencyVersionId versionId =
      new InternalDependencyVersionId(dependency, project.getPath(), project.getName(), null, group, version, null);
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  public static String str(Object o) {
    return String.valueOf(o == null ? "" : o);
  }

  public static void merge(Map<InternalDependencyVersionId, Scopes> map,
                           IdeRepoFileDependency dependency,
                           Map<String, Map<String, Collection<Configuration>>> userScopes) {
    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = deduceScope(configurationName, userScopes);
    if (scope == null) return;

    final ModuleVersionIdentifier dependencyId;
    if (dependency instanceof UnresolvedIdeRepoFileDependency) {
      UnresolvedIdeRepoFileDependency unresolvedDependency =
        (UnresolvedIdeRepoFileDependency)dependency;
      dependencyId = new MyModuleVersionIdentifier(unresolvedDependency.getFile().getName());
    }
    else {
      dependencyId = dependency.getId();
    }

    String classifier = parseClassifier(dependencyId, dependency.getFile());
    String dependencyFileName = null;
    if (dependency.getFile() != null) {
      dependencyFileName = dependency.getFile().getName();
    }

    InternalDependencyVersionId versionId =
      new InternalDependencyVersionId(dependency, dependencyId.getName(), dependencyId.getName(), dependencyFileName, dependencyId.getGroup(), dependencyId.getVersion(),
                                      classifier);
    Scopes scopes = map.get(versionId);
    if (scopes == null) {
      map.put(versionId, new Scopes(scope));
    }
    else {
      scopes.add(scope);
    }
  }

  public static String parseClassifier(ModuleVersionIdentifier dependencyId, File dependencyFile) {
    if (dependencyFile == null) return null;
    String dependencyFileName = dependencyFile.getName();
    int i = dependencyFileName.indexOf(dependencyId.getName() + '-' + dependencyId.getVersion() + '-');
    return i != -1 ? dependencyFileName.substring(i, dependencyFileName.length()) : null;
  }

  public static void merge(Map<InternalDependencyVersionId, Scopes> map,
                           IdeLocalFileDependency dependency,
                           Map<String, Map<String, Collection<Configuration>>> userScopes) {

    final String configurationName = dependency.getDeclaredConfiguration().getName();
    final GradleDependencyScope scope = deduceScope(configurationName, userScopes);
    if (scope == null) return;

    String path = dependency.getFile().getPath();
    InternalDependencyVersionId versionId =
      new InternalDependencyVersionId(dependency, path, path, dependency.getFile().getName(), "", "", null);
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
  public static GradleDependencyScope deduceScope(String configurationName,
                                                  Map<String, Map<String, Collection<Configuration>>> userScopes) {
    GradleDependencyScope scope = GradleDependencyScope.fromName(configurationName);
    for (Map.Entry<String, Map<String, Collection<Configuration>>> entry : userScopes.entrySet()) {
      Collection<Configuration> plusConfigurations = entry.getValue().get("plus");
      if (plusConfigurations == null) continue;

      for (Configuration plus : plusConfigurations) {
        if (plus.getName().equals(configurationName)) {
          return GradleDependencyScope.fromIdeaMappingName(entry.getKey().toLowerCase(Locale.ENGLISH));
        }
      }
    }

    return scope;
  }

  public static class MyModuleVersionIdentifier implements ModuleVersionIdentifier, Serializable {
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
