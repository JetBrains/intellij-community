// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class GradleBuildLayoutParametersCacheTest {

  private val project by projectFixture()
  private val projectPath by tempPathFixture()

  @Test
  fun `test cancelled cleanup registration does not retain cached parameters`() {
    val manager = GradleInstallationManager()
    val cancelledProject = mock<Project>()
    val cancellation = ProcessCanceledException()
    whenever(cancelledProject.getService(any<Class<Any>>())).thenThrow(cancellation)

    assertSame(cancellation, assertThrows<ProcessCanceledException> {
      manager.guessBuildLayoutParameters(cancelledProject, projectPath.toString())
    })

    val gradleUserHome = projectPath.resolve("gradle-user-home")
    GradleSettings.getInstance(project).serviceDirectoryPath = gradleUserHome.toString()
    val parameters = manager.guessBuildLayoutParameters(project, projectPath.toString())

    assertEquals(gradleUserHome, assertDoesNotThrow { parameters.gradleUserHomePath.maybeGetLocalValue() })
    assertSame(parameters, manager.guessBuildLayoutParameters(project, projectPath.toString()))
  }
}