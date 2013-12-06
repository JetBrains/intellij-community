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
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;
import org.jetbrains.plugins.gradle.model.ModelBuilderService;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.model.internal.IdeaContentRootImpl;
import org.jetbrains.plugins.gradle.model.internal.IdeaSourceDirectoryImpl;
import org.jetbrains.plugins.gradle.model.internal.ModuleExtendedModelImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModuleExtendedModelBuilderImpl implements ModelBuilderService {

  private static final String SOURCE_SETS_PROPERTY = "sourceSets";
  private static final String TEST_SRC_DIRS_PROPERTY = "testSrcDirs";

  @Override
  public boolean canBuild(String modelName) {
    return ModuleExtendedModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(String modelName, Project project) {

    final String moduleName = project.getName();
    final String moduleGroup = project.getGroup().toString();
    final String moduleVersion = project.getVersion().toString();

    final ModuleExtendedModelImpl moduleVersionModel = new ModuleExtendedModelImpl(moduleName, moduleGroup, moduleVersion);

    final List<File> artifacts = new ArrayList<File>();
    for (Task task : project.getTasks()) {
      if (task instanceof Jar) {
        Jar jar = (Jar)task;
        artifacts.add(jar.getArchivePath());
      }
    }

    moduleVersionModel.setArtifacts(artifacts);

    final Set<String> testDirectories = new HashSet<String>();
    for (Task task : project.getTasks()) {
      if (task instanceof Test) {
        Test test = (Test)task;
        if (test.hasProperty(TEST_SRC_DIRS_PROPERTY)) {
          Object testSrcDirs = test.property(TEST_SRC_DIRS_PROPERTY);
          if (testSrcDirs instanceof Iterable) {
            for (Object dir : Iterable.class.cast(testSrcDirs)) {
              addFilePath(testDirectories, dir);
            }
          }
        }
      }
    }

    final Set<String> javaDirectories = new HashSet<String>();
    final Set<String> resourceDirectories = new HashSet<String>();

    if (project.hasProperty(SOURCE_SETS_PROPERTY)) {
      Object sourceSets = project.property(SOURCE_SETS_PROPERTY);
      if (sourceSets instanceof SourceSetContainer) {
        SourceSetContainer sourceSetContainer = (SourceSetContainer)sourceSets;
        for (SourceSet sourceSet : sourceSetContainer) {
          for (File javaSrcDir : sourceSet.getAllJava().getSrcDirs()) {
            addFilePath(javaDirectories, javaSrcDir);
          }
          for (File resourcesSrcDir : sourceSet.getResources().getSrcDirs()) {
            addFilePath(resourceDirectories, resourcesSrcDir);
          }
        }
      }
    }


    File projectDir = project.getProjectDir();
    IdeaContentRootImpl contentRoot = new IdeaContentRootImpl(projectDir);

    enrichDataFromIdeaPlugin(project, contentRoot, javaDirectories, testDirectories);

    javaDirectories.removeAll(testDirectories);
    javaDirectories.removeAll(resourceDirectories);

    final Set<String> testResourceDirectories = new HashSet<String>(resourceDirectories);
    testResourceDirectories.retainAll(testDirectories);

    testDirectories.removeAll(resourceDirectories);
    resourceDirectories.removeAll(testResourceDirectories);

    for (String javaDir : javaDirectories) {
      contentRoot.addSourceDirectory(new IdeaSourceDirectoryImpl(new File(javaDir)));
    }
    for (String testDir : testDirectories) {
      contentRoot.addTestDirectory(new IdeaSourceDirectoryImpl(new File(testDir)));
    }
    for (String resourceDir : resourceDirectories) {
      contentRoot.addResourceDirectory(new IdeaSourceDirectoryImpl(new File(resourceDir)));
    }
    for (String testResourceDir : testResourceDirectories) {
      contentRoot.addTestResourceDirectory(new IdeaSourceDirectoryImpl(new File(testResourceDir)));
    }

    moduleVersionModel.setContentRoots(Collections.<ExtIdeaContentRoot>singleton(contentRoot));
    return moduleVersionModel;
  }

  private static void addFilePath(Set<String> filePathSet, Object file) {
    if (file instanceof File) {
      try {
        filePathSet.add(((File)file).getCanonicalPath());
      }
      catch (IOException ignore) {
      }
    }
  }

  private static void enrichDataFromIdeaPlugin(Project project,
                                               IdeaContentRootImpl contentRoot,
                                               Set<String> javaDirectories,
                                               Set<String> testDirectories) {

    IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);
    if (ideaPlugin == null) return;

    IdeaModel ideaModel = ideaPlugin.getModel();
    if (ideaModel == null || ideaModel.getModule() == null) return;

    for (File excludeDir : ideaModel.getModule().getExcludeDirs()) {
      contentRoot.addExcludeDirectory(excludeDir);
    }
    for (File file : ideaModel.getModule().getSourceDirs()) {
      javaDirectories.add(file.getPath());
    }
    for (File file : ideaModel.getModule().getTestSourceDirs()) {
      testDirectories.add(file.getPath());
    }
  }
}
