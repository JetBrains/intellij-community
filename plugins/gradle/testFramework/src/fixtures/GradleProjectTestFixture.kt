// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures

import com.intellij.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.gradle.util.GradleVersion

interface GradleProjectTestFixture : IdeaTestFixture {

  val gradleVersion: GradleVersion

  val gradleJvmFixture: GradleJvmTestFixture

  val fileFixture: FileTestFixture

  val projectName: String

  val project: Project

  /**
   * Root module of the project.
   */
  val module: Module

  /**
   * Main module of the project, `"${project.name}.main"`
   */
  val mainModule: Module
}