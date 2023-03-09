// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.gradle.util.GradleVersion

interface GradleProjectTestFixture : IdeaTestFixture {

  val gradleVersion: GradleVersion

  val fileFixture: FileTestFixture

  val projectName: String

  val project: Project

  val module: Module

  fun reloadProject()
}