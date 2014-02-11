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
package org.jetbrains.plugins.gradle.tooling.v1_12.builder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.internal.BuildScriptClasspathModelImpl;
import org.jetbrains.plugins.gradle.tooling.internal.ClasspathEntryModelImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vladislav.Soroka
 * @since 12/20/13
 */
@TargetVersions("1.12+")
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

    boolean offline = false;
    boolean downloadJavadoc = false;
    boolean downloadSources = true;

    final IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);

    if (ideaPlugin != null) {
      IdeaModel ideaModel = ideaPlugin.getModel();
      if (ideaModel != null && ideaModel.getModule() != null) {
        offline = ideaModel.getModule().isOffline();
        downloadJavadoc = ideaModel.getModule().isDownloadJavadoc();
        downloadSources = ideaModel.getModule().isDownloadSources();
      }
    }

    buildScriptClasspath = new BuildScriptClasspathModelImpl();
    Project parent = project.getParent();
    if (parent != null) {
      BuildScriptClasspathModelImpl parentBuildScriptClasspath = (BuildScriptClasspathModelImpl)buildAll(modelName, parent);
      if (parentBuildScriptClasspath != null) {
        for (ClasspathEntryModel classpathEntryModel : parentBuildScriptClasspath.getClasspath()) {
          buildScriptClasspath.add(classpathEntryModel);
        }
      }
    }

    final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor();

    final Configuration configuration = project.getBuildscript().getConfigurations().findByName("classpath");
    Collection<Configuration> plusConfigurations = Collections.singletonList(configuration);

    if (!offline) {
      // download sources and/or javadoc
      List<IdeExtendedRepoFileDependency> repoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
        project.getConfigurations(), plusConfigurations, new ArrayList<Configuration>(), downloadSources, downloadJavadoc);

      for (IdeExtendedRepoFileDependency dependency : repoFileDependencies) {
        if (dependency.getFile() == null) continue;

        buildScriptClasspath.add(
          new ClasspathEntryModelImpl(dependency.getFile(), dependency.getSourceFile(), dependency.getJavadocFile()));
      }
    }

    final Collection<IdeLocalFileDependency> localFileDependencies =
      dependenciesExtractor.extractLocalFileDependencies(plusConfigurations, new ArrayList<Configuration>());

    for (IdeLocalFileDependency dependency : localFileDependencies) {
      if (dependency.getFile() == null) continue;
      buildScriptClasspath.add(new ClasspathEntryModelImpl(dependency.getFile(), null, null));
    }

    cache.put(project.getPath(), buildScriptClasspath);
    return buildScriptClasspath;
  }
}
