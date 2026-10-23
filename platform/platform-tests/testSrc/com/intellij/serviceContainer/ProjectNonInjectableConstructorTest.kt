// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * IJPL-259452 The project container never calls a constructor that [NonInjectable] marks.
 */
@TestApplication
internal class ProjectNonInjectableConstructorTest {
  private val project by projectFixture()
  private val pluginId = PluginId.getId("project-non-injectable-constructor-test")

  @Test
  fun `a configurable bean in the project area gets the project`() {
    val bean = project.instantiateClass(ConfigurableEP::class.java, pluginId)
    assertThat(bean.project).isSameAs(project)
  }

  @Test
  fun `an extension skips a non-injectable project constructor and takes the no-arg constructor`() {
    assertThat(project.instantiateClass(NonInjectableProjectAndNoArg::class.java, pluginId).project).isNull()
  }

  @Test
  fun `without the annotation an extension takes the project constructor`() {
    assertThat(project.instantiateClass(ProjectAndNoArg::class.java, pluginId).project).isSameAs(project)
  }
}

private class NonInjectableProjectAndNoArg {
  val project: Project?

  @NonInjectable
  constructor(project: Project) {
    this.project = project
  }

  @Suppress("unused")
  constructor() {
    project = null
  }
}

private class ProjectAndNoArg {
  val project: Project?

  @Suppress("unused")
  constructor(project: Project) {
    this.project = project
  }

  @Suppress("unused")
  constructor() {
    project = null
  }
}
