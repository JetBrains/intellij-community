// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.tests.modelBuilder

import com.intellij.testFramework.utils.notImplemented
import org.gradle.api.Project
import java.io.File

class DummyProject(
  private val projectName: String,
  private val parent: DummyProject?,
) : Project by notImplemented(Project::class.java) {

  private val projectPath: String = when {
    parent == null -> ":"
    parent.parent == null -> ":$projectName"
    else -> parent.getPath() + ":$projectName"
  }

  private val projectDisplayName: String = when {
    parent == null -> "root project '$projectName'"
    else -> "project '$projectPath'"
  }

  override fun getBuildFile(): File = File("build.gradle")

  override fun getParent(): Project? = parent

  override fun getName(): String = projectName

  override fun getPath(): String = projectPath

  override fun getDisplayName(): String = projectDisplayName
}