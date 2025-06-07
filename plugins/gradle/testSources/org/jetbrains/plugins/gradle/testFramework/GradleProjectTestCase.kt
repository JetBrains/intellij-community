// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.utils.vfs.getFile
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

abstract class GradleProjectTestCase : GradleProjectBaseTestCase() {

  @get:JvmName("myProject")
  val project: Project get() = gradleFixture.project
  val module: Module get() = gradleFixture.module
  val projectRoot: VirtualFile get() = gradleFixture.fileFixture.root
  val projectPath: String get() = projectRoot.path

  fun isGradleAtLeast(version: String): Boolean = GradleVersionUtil.isGradleAtLeast(gradleVersion, version)
  fun isGradleOlderThan(version: String): Boolean = GradleVersionUtil.isGradleOlderThan(gradleVersion, version)

  fun testEmptyProject(gradleVersion: GradleVersion, test: () -> Unit) =
    test(gradleVersion, EMPTY_PROJECT, test)

  fun testJavaProject(gradleVersion: GradleVersion, test: () -> Unit) =
    test(gradleVersion, JAVA_PROJECT, test)

  fun testGroovyProject(gradleVersion: GradleVersion, test: () -> Unit) =
    test(gradleVersion, GROOVY_PROJECT, test)

  fun getFile(relativePath: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return projectRoot.getFile(relativePath)
  }

  fun createFile(relativePath: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return runWriteActionAndGet {
      projectRoot.createFile(relativePath)
    }
  }

  fun findOrCreateFile(relativePath: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return runWriteActionAndGet {
      projectRoot.findOrCreateFile(relativePath)
    }
  }

  fun writeText(relativePath: String, text: String): VirtualFile {
    val file = findOrCreateFile(relativePath)
    runWriteActionAndWait {
      file.writeText(text)
    }
    return file
  }


  fun writeTextAndCommit(relativePath: String, text: String): VirtualFile {
    val file = findOrCreateFile(relativePath)
    runWriteActionAndWait {
      file.writeTextAndCommit(text)
    }
    return file
  }

  @RequiresWriteLock
  private fun VirtualFile.writeTextAndCommit(text: String) {
    findDocument()?.reloadFromDisk()
    writeText(text)
    findDocument()?.commitToPsi(project)
  }

  fun appendText(relativePath: String, text: String): VirtualFile {
    val file = getFile(relativePath)
    runWriteActionAndWait {
      file.writeText(file.readText() + "\n" + text)
    }
    return file
  }

  fun prependText(relativePath: String, text: String): VirtualFile {
    val file = getFile(relativePath)
    runWriteActionAndWait {
      file.writeText(text + "\n" + file.readText())
    }
    return file
  }

  companion object {

    private val EMPTY_PROJECT = GradleTestFixtureBuilder.create("empty-project") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("empty-project")
      }
    }

    private val JAVA_PROJECT = GradleTestFixtureBuilder.create("java-plugin-project") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("java-plugin-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit()
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private val GROOVY_PROJECT = GradleTestFixtureBuilder.create("groovy-plugin-project") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("groovy-plugin-project")
      }
      withBuildFile(gradleVersion) {
        withGroovyPlugin()
      }
      withDirectory("src/main/java")
      withDirectory("src/main/groovy")
    }
  }
}