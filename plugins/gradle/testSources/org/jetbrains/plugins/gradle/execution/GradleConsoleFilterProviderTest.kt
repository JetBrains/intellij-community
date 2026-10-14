// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.filters.RegexpFilter
import com.intellij.testFramework.LightPlatform4TestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * @see GradleConsoleFilterProvider
 */
@RunWith(JUnit4::class)
class GradleConsoleFilterProviderTest : LightPlatform4TestCase() {

  override fun runInDispatchThread(): Boolean = false

  @Before
  fun before() {
    val settings = GradleProjectSettings().apply {
      externalProjectPath = project.basePath
    }
    GradleSettings.getInstance(project).linkProject(settings)
  }

  @After
  fun after() {
    GradleSettings.getInstance(project).unlinkExternalProject(project.basePath!!)
  }

  @Test
  fun shouldUseDefaultRegexpFilters() {
    val filters = GradleConsoleFilterProvider().getDefaultFilters(project)
    assertThat(filters).hasSize(2)
      .allSatisfy {
        assertThat(it).isInstanceOf(RegexpFilter::class.java)
      }
  }
}
