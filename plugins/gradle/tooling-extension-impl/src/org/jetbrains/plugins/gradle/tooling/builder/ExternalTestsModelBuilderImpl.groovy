// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.Test
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.tests.DefaultExternalTestSourceMapping
import org.jetbrains.plugins.gradle.model.tests.DefaultExternalTestsModel
import org.jetbrains.plugins.gradle.model.tests.ExternalTestSourceMapping
import org.jetbrains.plugins.gradle.model.tests.ExternalTestsModel
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil

@CompileStatic
class ExternalTestsModelBuilderImpl implements ModelBuilderService {
  @Override
  boolean canBuild(String modelName) {
    return ExternalTestsModel.name == modelName
  }

  @Override
  Object buildAll(String modelName, Project project) {
    def defaultTestsModel = new DefaultExternalTestsModel()
    // Projects using android plugins are not supported by this model builder.
    if (project.plugins.hasPlugin("com.android.base")) {
      return defaultTestsModel
    }
    if (JavaPluginUtil.isJavaPluginApplied(project)) {
      defaultTestsModel.sourceTestMappings = getMapping(project)
    }
    return defaultTestsModel
  }

  private static List<ExternalTestSourceMapping> getMapping(Project project) {
    def taskToClassesDirs = new LinkedHashMap<Test, Set<String>>()
    project.tasks.withType(Test.class, { Test task ->
      taskToClassesDirs.put(task, getClassesDirs(task))
    })

    def sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project)
    if (sourceSetContainer == null) return Collections.emptyList()
    def classesDirToSourceDirs = new LinkedHashMap<String, Set<String>>()
    for (sourceSet in sourceSetContainer) {
      def sourceDirectorySet = sourceSet.allSource
      List<String> sourceFolders = []
      for (File dir : sourceDirectorySet.srcDirs) {
        sourceFolders.add(dir.absolutePath)
      }
      for (classDirectory in getPaths(sourceSet.output)) {
        def storedSourceFolders = classesDirToSourceDirs.get(classDirectory)
        if (storedSourceFolders == null) {
          storedSourceFolders = new LinkedHashSet<String>()
        }
        storedSourceFolders.addAll(sourceFolders)
        classesDirToSourceDirs.put(classDirectory, storedSourceFolders)
      }
    }
    def testSourceMappings = new ArrayList<ExternalTestSourceMapping>()
    for (entry in taskToClassesDirs.entrySet()) {
      def sourceFolders = new LinkedHashSet<String>()
      for (classDirectory in entry.value) {
        def storedSourceFolders = classesDirToSourceDirs.get(classDirectory)
        if (storedSourceFolders == null) continue
        for (folder in storedSourceFolders) sourceFolders.add(folder)
      }
      def task = entry.key
      def defaultExternalTestSourceMapping = new DefaultExternalTestSourceMapping()
      defaultExternalTestSourceMapping.testName = task.name
      defaultExternalTestSourceMapping.testTaskPath = task.path
      defaultExternalTestSourceMapping.sourceFolders = sourceFolders
      testSourceMappings.add(defaultExternalTestSourceMapping)
    }
    return testSourceMappings
  }

  @CompileDynamic
  private static Set<String> getClassesDirs(Test test) {
    def testClassesDirs = new LinkedHashSet()
    if (test.hasProperty("testClassesDirs")) {
      testClassesDirs.addAll(getPaths(test.testClassesDirs))
    }
    if (test.hasProperty("testClassesDir")) {
      def testClassesDir = test.testClassesDir?.absolutePath
      if (testClassesDir != null) {
        testClassesDirs.add(testClassesDir)
      }
    }
    return testClassesDirs
  }

  private static Set<String> getPaths(FileCollection files) {
    def paths = new LinkedHashSet<String>()
    for (def file : files.files) {
      paths.add(file.absolutePath)
    }
    return paths
  }

  @NotNull
  @Override
  ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(project, e, "Tests model errors")
  }
}
