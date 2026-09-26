// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.asStream

@TestApplication
class WrongPluginModuleAliasInspectionTest {

  private val tempDir: TestFixture<Path> = tempPathFixture()
  private val project: TestFixture<Project> = projectFixture(tempDir, openAfterCreation = true)

  @Suppress("unused") // required by codeInsightFixture
  private val module by project.moduleFixture(tempDir, addPathToSourceRoot = true)
  private val fixture = codeInsightFixture(project, tempDir)

  @BeforeEach
  fun setUp() {
    fixture.get().enableInspections(WrongPluginModuleAliasInspection())
  }

  @DisplayName("Plugin module alias inspection test")
  @ParameterizedTest(name = "{0} when vendor is ''{1}'', module prefix is ''{2}'', and IntelliJ Platform project is {3}")
  @MethodSource("testCases")
  fun testPluginModuleAlias(
    testExpectationPrefix: String,
    vendor: String,
    modulePrefix: String,
    isIntelliJProject: Boolean,
  ) {
    if (isIntelliJProject) {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project.get(), true)
    }

    val moduleValue = "${modulePrefix}myFeature"
    val errorTag = if (testExpectationPrefix == ERROR_EXPECTED) {
      """<error descr="Plugin module alias must not start with 'com.intellij.*' in non-JetBrains plugins">$moduleValue</error>"""
    }
    else {
      moduleValue
    }

    doTest("""
      <idea-plugin>
        <vendor>$vendor</vendor>
        <module value="$errorTag"/>
      </idea-plugin>
    """.trimIndent())
  }

  private fun doTest(@Language("XML") xml: String) {
    fixture.get().apply {
      configureByText("plugin.xml", xml)
      testHighlighting(false, false, true)
    }
  }

  companion object {
    private const val ERROR_EXPECTED = "should report error"
    private const val ERROR_NOT_EXPECTED = "should not report error"

    @JvmStatic
    fun testCases(): Stream<Arguments> =
      cartesianProduct(
        listOf("JetBrains", "JetBrains s.r.o.", "JetBrains, Google", "Acme"),
        listOf("com.intellij.", "com.example."),
        listOf(true, false)
      ).map { (vendor, prefix, isIntelliJProject) ->
        val expectError = !PluginManagerCore.isDevelopedByJetBrains(vendor) && prefix == "com.intellij." && !isIntelliJProject
        val testExpectationPrefix = if (expectError) ERROR_EXPECTED else ERROR_NOT_EXPECTED
        Arguments.of(testExpectationPrefix, vendor, prefix, isIntelliJProject)
      }.asStream()

    private fun <A, B, C> cartesianProduct(
      listA: List<A>,
      listB: List<B>,
      listC: List<C>,
    ): Sequence<Triple<A, B, C>> = sequence {
      for (a in listA) {
        for (b in listB) {
          for (c in listC) {
            yield(Triple(a, b, c))
          }
        }
      }
    }
  }
}
