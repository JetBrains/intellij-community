// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.vfs.getFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleAtLeast
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleOlderThan

abstract class GradleProjectTestCase : GradleProjectBaseTestCase() {

  @get:JvmName("myProject")
  val project: Project get() = gradleFixture.project
  val projectRoot: VirtualFile get() = gradleFixture.fileFixture.root
  val projectPath: String get() = projectRoot.path
  val gradleVersion: GradleVersion get() = gradleFixture.gradleVersion

  fun isGradleAtLeast(version: String): Boolean = gradleVersion.isGradleAtLeast(version)
  fun isGradleOlderThan(version: String): Boolean = gradleVersion.isGradleOlderThan(version)

  fun findOrCreateFile(relativePath: String, text: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return runWriteActionAndGet {
      val file = projectRoot.findOrCreateFile(relativePath)
      file.findDocument()?.reloadFromDisk()
      file.writeText(text)
      file.findDocument()?.commitToPsi(project)
      file
    }
  }

  fun getFile(relativePath: String): VirtualFile {
    return projectRoot.getFile(relativePath)
  }
}