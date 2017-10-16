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
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaCompilerOutputImpl;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaContentRootImpl;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaSourceDirectoryImpl;
import org.jetbrains.plugins.gradle.tooling.internal.ModuleExtendedModelImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @deprecated to be removed in 2018.1
 *
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModuleExtendedModelBuilderImpl implements ModelBuilderService {

  private static boolean is4OorBetter = GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.0")) >= 0;

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
    final File buildDir = project.getBuildDir();

    String javaSourceCompatibility = null;
    for (Task task : project.getTasks()) {
      if (task instanceof JavaCompile) {
        JavaCompile javaCompile = (JavaCompile)task;
        javaSourceCompatibility = javaCompile.getSourceCompatibility();
        if(task.getName().equals("compileJava")) break;
      }
    }

    final ModuleExtendedModelImpl moduleVersionModel =
      new ModuleExtendedModelImpl(moduleName, moduleGroup, moduleVersion, buildDir, javaSourceCompatibility);

    final List<File> artifacts = new ArrayList<File>();
    for (Task task : project.getTasks()) {
      if (task instanceof Jar) {
        Jar jar = (Jar)task;
        artifacts.add(jar.getArchivePath());
      }
    }

    moduleVersionModel.setArtifacts(artifacts);

    final Set<String> sourceDirectories = new HashSet<String>();
    final Set<String> testDirectories = new HashSet<String>();
    final Set<String> resourceDirectories = new HashSet<String>();
    final Set<String> testResourceDirectories = new HashSet<String>();

    final List<File> testClassesDirs = new ArrayList<File>();
    for (Task task : project.getTasks()) {
      if (task instanceof Test) {
        Test test = (Test)task;
        if (is4OorBetter) {
          testClassesDirs.addAll(test.getTestClassesDirs().getFiles());
        }
        else {
          testClassesDirs.add(test.getTestClassesDir());
        }

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

    IdeaCompilerOutputImpl compilerOutput = new IdeaCompilerOutputImpl();

    if (project.hasProperty(SOURCE_SETS_PROPERTY)) {
      Object sourceSets = project.property(SOURCE_SETS_PROPERTY);
      if (sourceSets instanceof SourceSetContainer) {
        SourceSetContainer sourceSetContainer = (SourceSetContainer)sourceSets;
        for (SourceSet sourceSet : sourceSetContainer) {

          SourceSetOutput output = sourceSet.getOutput();
          if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
            if (is4OorBetter) {
              File firstClassesDir = CollectionUtils.findFirst(output.getClassesDirs().getFiles(), Specs.SATISFIES_ALL);
              compilerOutput.setTestClassesDir(firstClassesDir);
            }
            else {
              compilerOutput.setTestClassesDir(output.getClassesDir());
            }
            compilerOutput.setTestResourcesDir(output.getResourcesDir());
          }
          if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
            if (is4OorBetter) {
              File firstClassesDir = CollectionUtils.findFirst(output.getClassesDirs().getFiles(), Specs.SATISFIES_ALL);
              compilerOutput.setMainClassesDir(firstClassesDir);
            }
            else {
              compilerOutput.setMainClassesDir(output.getClassesDir());
            }
            compilerOutput.setMainResourcesDir(output.getResourcesDir());
          }

          for (File javaSrcDir : sourceSet.getAllJava().getSrcDirs()) {
            boolean isTestDir = isTestDir(sourceSet, testClassesDirs);
            addFilePath(isTestDir ? testDirectories : sourceDirectories, javaSrcDir);
          }
          for (File resourcesSrcDir : sourceSet.getResources().getSrcDirs()) {
            boolean isTestDir = isTestDir(sourceSet, testClassesDirs);
            addFilePath(isTestDir ? testResourceDirectories : resourceDirectories, resourcesSrcDir);
          }
        }
      }
    }

    File projectDir = project.getProjectDir();
    IdeaContentRootImpl contentRoot = new IdeaContentRootImpl(projectDir);

    final Set<String> ideaSourceDirectories = new HashSet<String>();
    final Set<String> ideaTestDirectories = new HashSet<String>();
    final Set<String> ideaGeneratedDirectories = new HashSet<String>();
    final Set<File> excludeDirectories = new HashSet<File>();

    enrichDataFromIdeaPlugin(project, excludeDirectories, ideaSourceDirectories, ideaTestDirectories, ideaGeneratedDirectories);

    if (ideaSourceDirectories.isEmpty()) {
      sourceDirectories.clear();
      resourceDirectories.clear();
    }
    if (ideaTestDirectories.isEmpty()) {
      testDirectories.clear();
      testResourceDirectories.clear();
    }

    ideaSourceDirectories.removeAll(resourceDirectories);
    sourceDirectories.removeAll(ideaTestDirectories);
    sourceDirectories.addAll(ideaSourceDirectories);
    ideaTestDirectories.removeAll(testResourceDirectories);
    testDirectories.addAll(ideaTestDirectories);

    // ensure disjoint directories with different type
    resourceDirectories.removeAll(sourceDirectories);
    testDirectories.removeAll(sourceDirectories);
    testResourceDirectories.removeAll(testDirectories);

    for (String javaDir : sourceDirectories) {
      contentRoot.addSourceDirectory(new IdeaSourceDirectoryImpl(new File(javaDir), ideaGeneratedDirectories.contains(javaDir)));
    }
    for (String testDir : testDirectories) {
      contentRoot.addTestDirectory(new IdeaSourceDirectoryImpl(new File(testDir), ideaGeneratedDirectories.contains(testDir)));
    }
    for (String resourceDir : resourceDirectories) {
      contentRoot.addResourceDirectory(new IdeaSourceDirectoryImpl(new File(resourceDir)));
    }
    for (String testResourceDir : testResourceDirectories) {
      contentRoot.addTestResourceDirectory(new IdeaSourceDirectoryImpl(new File(testResourceDir)));
    }
    for (File excludeDir : excludeDirectories) {
      contentRoot.addExcludeDirectory(excludeDir);
    }

    moduleVersionModel.setContentRoots(Collections.<ExtIdeaContentRoot>singleton(contentRoot));
    moduleVersionModel.setCompilerOutput(compilerOutput);

    ConfigurationContainer configurations = project.getConfigurations();
    SortedMap<String, Configuration> configurationsByName = configurations.getAsMap();

    Map<String, Set<File>> artifactsByConfiguration = new HashMap<String, Set<File>>();
    for (Map.Entry<String, Configuration> configurationEntry : configurationsByName.entrySet()) {
      Set<File> files = configurationEntry.getValue().getAllArtifacts().getFiles().getFiles();
      artifactsByConfiguration.put(configurationEntry.getKey(), files);
    }
    moduleVersionModel.setArtifactsByConfiguration(artifactsByConfiguration);

    return moduleVersionModel;
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Other"
    ).withDescription("Unable to resolve all content root directories");
  }

  private static boolean isTestDir(SourceSet sourceSet, List<File> testClassesDirs) {
    if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) return true;
    if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) return false;

    File sourceSetClassesDir;
    if (is4OorBetter) {
      sourceSetClassesDir = CollectionUtils.findFirst(sourceSet.getOutput().getClassesDirs().getFiles(), Specs.SATISFIES_ALL);
    }
    else {
      sourceSetClassesDir = sourceSet.getOutput().getClassesDir();
    }
    for (File testClassesDir : testClassesDirs) {
      do {
        if (sourceSetClassesDir.getPath().equals(testClassesDir.getPath())) return true;
      }
      while ((testClassesDir = testClassesDir.getParentFile()) != null);
    }

    return false;
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
                                               Set<File> excludeDirectories,
                                               Set<String> javaDirectories,
                                               Set<String> testDirectories,
                                               Set<String> ideaGeneratedDirectories) {

    IdeaPlugin ideaPlugin = project.getPlugins().findPlugin(IdeaPlugin.class);
    if (ideaPlugin == null) return;

    IdeaModel ideaModel = ideaPlugin.getModel();
    if (ideaModel == null || ideaModel.getModule() == null) return;

    for (File excludeDir : ideaModel.getModule().getExcludeDirs()) {
      excludeDirectories.add(excludeDir);
    }
    for (File file : ideaModel.getModule().getSourceDirs()) {
      javaDirectories.add(file.getPath());
    }
    for (File file : ideaModel.getModule().getTestSourceDirs()) {
      testDirectories.add(file.getPath());
    }

    if(GradleVersion.current().compareTo(GradleVersion.version("2.2")) >=0) {
      for (File file : ideaModel.getModule().getGeneratedSourceDirs()) {
        ideaGeneratedDirectories.add(file.getPath());
      }
    }
  }
}
