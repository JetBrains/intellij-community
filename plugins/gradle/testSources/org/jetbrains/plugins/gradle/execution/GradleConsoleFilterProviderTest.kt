// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.LightPlatform4TestCase
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.function.Consumer

/**
 * @see GradleConsoleFilterProvider
 */
@RunWith(JUnit4::class)
class GradleConsoleFilterProviderTest : LightPlatform4TestCase() {
  override fun runInDispatchThread(): Boolean =
    false

  @Before
  fun before() {
    val settings = GradleProjectSettings().apply {
      externalProjectPath = project.basePath
    }
    GradleSettings.getInstance(project).linkProject(settings)
  }

  @After
  fun after() {
    GradleSettings.getInstance(project).unlinkExternalProject(project.basePath.assertNotNull())
  }

  /**
   * Should contain at least one instance of [GradleConsoleFilter] and at least
   * one instance of [RegexpFilter].
   */
  @Test
  fun filterTypesShouldBeAvailable() {
    assertSoftly { softly ->
      with(softly) {
        val consoleFilterProvider = GradleConsoleFilterProvider()

        val filters = consoleFilterProvider.getDefaultFilters(project)

        assertThat(filters).anySatisfy(Consumer { filter ->
          assertThat(filter).isInstanceOf(GradleConsoleFilter::class.java)
        }).anySatisfy(Consumer { filter ->
          assertThat(filter).isInstanceOf(RegexpFilter::class.java)
        })
      }
    }
  }

  @Test
  fun regexpFilterShouldReturnNullForNonMatchingLines() {
    assertSoftly { softly ->
      with(softly) {
        val consoleFilterProvider = GradleConsoleFilterProvider()

        val filters = consoleFilterProvider.getDefaultFilters(project)

        val regexpFilter = filters.asSequence().filterIsInstance<RegexpFilter>().first()

        assertThat(regexpFilter.applyFilter("foo", 42)).isNull()
        assertThat(regexpFilter.applyFilter("/foo/bar", 42)).isNull()
      }
    }
  }

  @Test
  fun absoluteUnixPath() {
    testAbsolutePath(absolutePath = "/etc/passwd")
  }

  @Test
  fun absoluteUnixPathWithErrorText() {
    testAbsolutePath(absolutePath = "/etc/passwd",
                     errorText = "Error text")
  }

  @Test
  fun absoluteWindowsPath() {
    testAbsolutePath(absolutePath = """c:\autoexec.bat""")
  }

  @Test
  fun absoluteWindowsPathWithErrorText() {
    testAbsolutePath(absolutePath = """c:\autoexec.bat""",
                     errorText = "Error text")
  }

  private fun testAbsolutePath(absolutePath: @NlsSafe String, errorText: @Nls String? = null) {
    assertSoftly { softly ->
      with(softly) {
        val consoleFilterProvider = GradleConsoleFilterProvider()

        val filters = consoleFilterProvider.getDefaultFilters(project)

        val regexpFilter = filters.asSequence().filterIsInstance<RegexpFilter>().first()

        val line0 = message(absolutePath, line = 42, errorText = errorText)
        val result0 = regexpFilter.applyFilter(line0, line0.length)
        assertThat(result0).isNotNull
        assertThat(result0.assertNotNull().resultItems[0]).satisfies(Consumer { resultItem ->
          assertThat(resultItem).isNotNull
          assertThat(resultItem.highlightStartOffset).isZero
          assertThat(resultItem.highlightEndOffset).isEqualTo(absolutePath.length + 3)
        })

        val line1 = message(absolutePath, line = 42, column = 42, errorText)
        val result1 = regexpFilter.applyFilter(line1, line1.length)
        assertThat(result1.assertNotNull().resultItems[0]).satisfies(Consumer { resultItem ->
          assertThat(resultItem).isNotNull
          assertThat(resultItem.highlightStartOffset).isZero
          assertThat(resultItem.highlightEndOffset).isEqualTo(absolutePath.length + 6)
        })
      }
    }
  }

  private companion object {
    private fun message(absolutePath: @NlsSafe String,
                        line: Int,
                        column: Int = -1,
                        errorText: @Nls String? = null): @Nls String {
      val prefix = when (column) {
        -1 -> "$absolutePath:$line"
        else -> "$absolutePath:$line:$column"
      }

      return when (errorText) {
        null -> prefix
        else -> "$prefix: $errorText"
      }
    }

    /**
     * Verifies that the actual value is not `null`.
     */
    private fun <T : Any> T?.assertNotNull(): T {
      Assertions.assertThat(this).isNotNull

      return this!!
    }
  }
}
