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
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.ModuleLibrary;
import org.gradle.plugins.ide.idea.model.Path;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.BuildScriptClasspathModelImpl;
import org.jetbrains.plugins.gradle.tooling.internal.ClasspathEntryModelImpl;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vladislav.Soroka
 * @since 12/20/13
 */
public class ModelBuildScriptClasspathBuilderImpl implements ModelBuilderService {

  private final Map<String, BuildScriptClasspathModelImpl> cache = new ConcurrentHashMap<String, BuildScriptClasspathModelImpl>();

  @Override
  public boolean canBuild(String modelName) {
    return BuildScriptClasspathModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(final String modelName, final Project project) {
    BuildScriptClasspathModelImpl buildScriptClasspath = cache.get(project.getPath());
    if (buildScriptClasspath != null) return buildScriptClasspath;

    buildScriptClasspath = new BuildScriptClasspathModelImpl();
    final File gradleHomeDir = project.getGradle().getGradleHomeDir();
    buildScriptClasspath.setGradleHomeDir(gradleHomeDir);
    buildScriptClasspath.setGradleVersion(GradleVersion.current().getVersion());

    final IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);
    if (ideaPlugin != null) {
      Project parent = project.getParent();
      if (parent != null) {
        BuildScriptClasspathModelImpl parentBuildScriptClasspath = (BuildScriptClasspathModelImpl)buildAll(modelName, parent);
        if (parentBuildScriptClasspath != null) {
          for (ClasspathEntryModel classpathEntryModel : parentBuildScriptClasspath.getClasspath()) {
            buildScriptClasspath.add(classpathEntryModel);
          }
        }
      }
      Configuration configuration = project.getBuildscript().getConfigurations().findByName("classpath");
      if (configuration == null) return null;
      configuration = configuration.copy();
      Collection<Configuration> plusConfigurations = Collections.singletonList(configuration);

      final Map<String, Map<String, Collection<Configuration>>> scopes =
        new HashMap<String, Map<String, Collection<Configuration>>>(ideaPlugin.getModel().getModule().getScopes());

      Map<String, Map<String, Collection<Configuration>>> buildScriptScope = new HashMap<String, Map<String, Collection<Configuration>>>();
      Map<String, Collection<Configuration>> plusConfiguration = new HashMap<String, Collection<Configuration>>();
      plusConfiguration.put("plus", plusConfigurations);
      if (scopes.get("COMPILE") != null) {
        plusConfiguration.put("minus", scopes.get("COMPILE").get("plus"));
      }
      buildScriptScope.put("COMPILE", plusConfiguration);
      ideaPlugin.getModel().getModule().setScopes(buildScriptScope);
      ideaPlugin.getModel().getModule().getProject().getConfigurations().add(configuration);
      final Set<Dependency> buildScriptDependencies = ideaPlugin.getModel().getModule().resolveDependencies();
      for (Dependency dependency : buildScriptDependencies) {
        if (dependency instanceof ModuleLibrary) {
          ModuleLibrary moduleLibrary = (ModuleLibrary)dependency;
          if ("COMPILE".equals(moduleLibrary.getScope())) {
            buildScriptClasspath.add(new ClasspathEntryModelImpl(
              convert(moduleLibrary.getClasses()), convert(moduleLibrary.getSources()), convert(moduleLibrary.getJavadoc())));
          }
        }
      }

      ideaPlugin.getModel().getModule().setScopes(scopes);
      ideaPlugin.getModel().getModule().getProject().getConfigurations().remove(configuration);
    }

    cache.put(project.getPath(), buildScriptClasspath);
    return buildScriptClasspath;
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Project build classpath resolve errors"
    ).withDescription("Some codeInsight features may not work for gradle build script");
  }

  private static Set<String> convert(Set<Path> paths) {
    Set<String> result = new HashSet<String>(paths.size());
    for (Path path : paths) {
      result.add(path.getRelPath());
    }
    return result;
  }
}
