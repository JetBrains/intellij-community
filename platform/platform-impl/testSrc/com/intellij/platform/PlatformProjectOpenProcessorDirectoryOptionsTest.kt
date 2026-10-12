// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Checks the options that the directory branch of [PlatformProjectOpenProcessor] opens a directory with.
 * A `beforeOpen` task in them would make the project manager initialize the frame only after the whole project preparation.
 */
@TestApplication
internal class PlatformProjectOpenProcessorDirectoryOptionsTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val directory = Path.of("directory")

  @Test
  fun `a beforeInit task marks the project and no beforeOpen task is added`() {
    val options = PlatformProjectOpenProcessor.directoryOpenOptions(OpenProjectTask {}, directory)

    assertThat(options.beforeOpen).isNull()
    val project = MockProject(null, disposable)
    options.beforeInit!!.invoke(project)
    assertThat(PlatformProjectOpenProcessor.isOpenedByPlatformProcessor(project)).isTrue()
  }

  @Test
  fun `the configurators run and the directory is the project root by default`() {
    val options = PlatformProjectOpenProcessor.directoryOpenOptions(OpenProjectTask {}, directory)

    assertThat(options.runConfigurators).isTrue()
    assertThat(options.projectRootDir).isEqualTo(directory)
  }

  @Test
  fun `the original options are kept`() {
    val rootDir = Path.of("root")
    var isOriginalBeforeInitInvoked = false
    val options = PlatformProjectOpenProcessor.directoryOpenOptions(OpenProjectTask {
      runConfigurators = false
      projectRootDir = rootDir
      beforeInitTasks += { isOriginalBeforeInitInvoked = true }
    }, directory)

    assertThat(options.runConfigurators).isFalse()
    assertThat(options.projectRootDir).isEqualTo(rootDir)
    options.beforeInit!!.invoke(MockProject(null, disposable))
    assertThat(isOriginalBeforeInitInvoked).isTrue()
  }

  @Test
  fun `an original beforeOpen task is kept and can reset the mark`(): Unit = timeoutRunBlocking {
    val options = PlatformProjectOpenProcessor.directoryOpenOptions(OpenProjectTask {
      beforeOpenTasks += { project ->
        project.putUserData(PROJECT_OPENED_BY_PLATFORM_PROCESSOR, false)
        true
      }
    }, directory)

    // the project manager calls `beforeInit` when it instantiates the project, and `beforeOpen` after it prepares the project
    val project = MockProject(null, disposable)
    options.beforeInit!!.invoke(project)
    assertThat(options.beforeOpen!!.invoke(project)).isTrue()
    assertThat(PlatformProjectOpenProcessor.isOpenedByPlatformProcessor(project)).isFalse()
  }
}
