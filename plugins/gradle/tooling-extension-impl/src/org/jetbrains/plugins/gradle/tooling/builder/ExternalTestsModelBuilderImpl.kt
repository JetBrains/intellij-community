// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.Test
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.tests.DefaultExternalTestSourceMapping
import org.jetbrains.plugins.gradle.model.tests.DefaultExternalTestsModel
import org.jetbrains.plugins.gradle.model.tests.ExternalTestSourceMapping
import org.jetbrains.plugins.gradle.model.tests.ExternalTestsModel
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File

class ExternalTestsModelBuilderImpl : ModelBuilderService {
  override fun canBuild(modelName: String?): Boolean {
    return ExternalTestsModel::class.java.name == modelName
  }

  override fun buildAll(modelName: String, project: Project): Any {
    val defaultTestsModel = DefaultExternalTestsModel()
    // Projects using android plugins are not supported by this model builder.
    if (project.plugins.hasPlugin("com.android.base")) {
      return defaultTestsModel
    }
    if (JavaPluginUtil.isJavaPluginApplied(project)) {
      defaultTestsModel.setSourceTestMappings(getMapping(project))
    }
    return defaultTestsModel
  }

  private fun getMapping(project: Project): List<ExternalTestSourceMapping> {
    val taskToClassesDirs = LinkedHashMap<Test, MutableSet<String>>()
    val tasks = project.tasks.withType(Test::class.javaObjectType)
    for (task in tasks) {
      taskToClassesDirs[task] = getClassesDirs(task)
    }
    val sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project) ?: return emptyList()
    val classesDirToSourceDirs = LinkedHashMap<String, MutableSet<String>>()
    for (sourceSet in sourceSetContainer) {
      val sourceDirectorySet = sourceSet.allSource
      val sourceFolders = mutableListOf<String>()
      for (dir in sourceDirectorySet.srcDirs) {
        sourceFolders.add(dir.absolutePath)
      }
      for (classDirectory in getPaths(sourceSet.output)) {
        var storedSourceFolders: MutableSet<String>? = classesDirToSourceDirs[classDirectory]
        if (storedSourceFolders == null) {
          storedSourceFolders = LinkedHashSet()
        }
        storedSourceFolders.addAll(sourceFolders)
        classesDirToSourceDirs[classDirectory] = storedSourceFolders
      }
    }

    val testSourceMappings = ArrayList<ExternalTestSourceMapping>()
    for ((task, classesDirs) in taskToClassesDirs) {
      val sourceFolders = LinkedHashSet<String>()
      for (classDirectory in classesDirs) {
        val storedSourceFolders = classesDirToSourceDirs.get(classDirectory)
        if (storedSourceFolders == null) continue
        for (folder in storedSourceFolders) sourceFolders.add(folder)
      }
      val defaultExternalTestSourceMapping = DefaultExternalTestSourceMapping()
      defaultExternalTestSourceMapping.setTestName(task.name)
      defaultExternalTestSourceMapping.testTaskPath = task.path
      defaultExternalTestSourceMapping.sourceFolders = sourceFolders
      testSourceMappings.add(defaultExternalTestSourceMapping)
    }
    return testSourceMappings
  }

  private fun getClassesDirs(test: Test): MutableSet<String> {
    val testClassesDirs = LinkedHashSet<String>()
    if (test.hasProperty("testClassesDirs")) {
      testClassesDirs.addAll(getPaths(test.testClassesDirs))
    }
    if (test.hasProperty("testClassesDir")) {
      val testClassesDir: File? = test.property("testClassesDir") as? File
      if (testClassesDir != null) {
        testClassesDirs.add(testClassesDir.absolutePath)
      }
    }
    return testClassesDirs
  }

  private fun getPaths(files: FileCollection): MutableSet<String> {
    val paths = LinkedHashSet<String>()
    for (file in files.files) {
      paths.add(file.absolutePath)
    }
    return paths
  }

  override fun reportErrorMessage(
    @NotNull modelName: String,
    @NotNull project: Project,
    @NotNull context: ModelBuilderContext,
    @NotNull exception: Exception
  ) {
    context.messageReporter.createMessage()
      .withGroup(Messages.TEST_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Test model failure")
      .withException(exception)
      .reportMessage(project)
  }
}