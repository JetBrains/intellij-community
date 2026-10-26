// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestApplication
class GradleKotlinErrorReporterOutputParserTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("testData")
  fun `test messages are parsed`(case: TestCase) {
    val events = parseMessage(case.output)

    assertEquals(case.expected.size, events.size) { "Actual: $events" }

    for ((expected, event) in case.expected.zip(events)) {
      val messageEvent = assertInstanceOf<MessageEvent>(event)
      assertEquals(expected.kind, messageEvent.kind)
      assertEquals(expected.message, messageEvent.message)
      assertEquals(expected.description, messageEvent.description?.trimEnd())
      assertEquals(expected.description, messageEvent.result.details?.trimEnd())
    }
  }

  private fun parseMessage(output: String): List<BuildEvent> {
    val events = mutableListOf<BuildEvent>()
    BuildOutputInstantReaderImpl(
      StartId(),
      StartId(),
      BuildProgressListener { _, event -> events += event },
      listOf(GradleKotlinErrorReporterOutputParser())
    )
      .use { it.append(output + "\n") }
    return events
  }

  class TestCase(val output: String, vararg val expected: ExpectedEvent) {
    override fun toString(): String = output.lineSequence().first()
  }

  class ExpectedEvent(val kind: MessageEvent.Kind, val message: String, val description: String)

  companion object {
    private val KOTLIN_GRADLE_PLUGIN_ERROR = """
      e: Some Kotlin Gradle Plugin Error
      Solution: Please fix the build configuration.
    """.trimIndent()

    private val UNSUPPORTED_KOTLIN_PLUGIN_VERSION_WARNING = """
      WARNING: Unsupported Kotlin plugin version.
      The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `2.3.20` that might work differently than in the requested version `2.5.0-dev-498`.
      Using the `kotlin-dsl` plugin together with a different Kotlin version (for example, by using the Kotlin Gradle plugin (`kotlin(jvm)`)) in the same project is not recommended.

      It applies a certain version of the `org.jetbrains.kotlin.jvm` plugin and also add a dependency on the Kotlin Standard Library.

      Applying other version of the `org.jetbrains.kotlin.jvm` plugin in the build and/or adding dependencies to different versions of the Kotlin Standard Library can cause incompatibilities.
    """.trimIndent()

    private val DEPRECATED_GRADLE_PROPERTY_WARNING = """
      w: Deprecated Gradle Property 'kotlin.compiler.runViaBuildToolsApi' Used
      The `kotlin.compiler.runViaBuildToolsApi` deprecated property is used in your build.
      Solutions:
       * It is unsupported, please stop using it.
       * Kotlin Gradle plugin has run JVM compilations using the Build Tools API by default since Kotlin 2.3.20. The legacy mode is deprecated and will be removed in Kotlin 2.5.0. Please create an issue if something is not working correctly when the Build Tools API is active: https://kotl.in/issue
    """.trimIndent()

    private val INTERNAL_KOTLIN_GRADLE_PLUGIN_PROPERTIES_WARNING = """
      w: Usage of Internal Kotlin Gradle Plugin Properties Detected
      ATTENTION! This build uses the following Kotlin Gradle Plugin properties:

      kotlin.internal.suppress.buildToolsApiVersionConsistencyChecks

      Internal properties are not recommended for production use.
      Stability and future compatibility of the build is not guaranteed.
      Solution: Please consider using the public API instead of internal properties.
    """.trimIndent()

    private val JS_ENVIRONMENT_NOT_SELECTED_WARNING = """
      w: JS Environment Not Selected
      Please choose a JavaScript environment to build distributions and run tests.
      Not choosing any of them will be an error in the future releases.
      kotlin {
          js {
              // To build distributions for and run tests use one or several of:
              browser()
              nodejs()
          }
      }
      Solution: Please choose a JavaScript environment to build distributions and run tests.
    """.trimIndent()

    private val DEFAULT_KOTLIN_HIERARCHY_TEMPLATE_WARNING = """
      w: Default Kotlin Hierarchy Template Misconfiguration Due to Illegal Target Names
      The Default Kotlin Hierarchy Template was not applied to 'project ':kotlin-power-assert-runtime'':
      Source sets created by the following targets will clash with source sets created by the template:
      [native]

      Consider renaming the targets or disabling the default template by adding
          'kotlin.mpp.applyDefaultHierarchyTemplate=false'
      to your gradle.properties
      Solution: Please rename the targets or disable the default template.
    """.trimIndent()

    private val UNSUPPORTED_API_DEPENDENCY_TYPES_WARNING = """
      w: Unsupported API dependency types in test source sets
      API dependency types are used in test source sets
      Dependencies:
          - kotlin:kotlin-test:unspecified (source sets: jsTest, jvmTest, nativeTest, wasmJsTest, wasmWasiTest)

      Adding API dependency types to test source sets is not supported and will removed in a future version of Kotlin.

      API dependencies are transitively exposed to consumers, but test source sets should not be consumable.
      Solutions:
       * Replace API dependency types in test source sets with implementation dependencies.
    """.trimIndent()

    private val INVALID_SOURCE_SET_DEPENDENCY_MAIN_WARNING = """
      w: Invalid Source Set Dependency Across Trees
          Following Kotlin Source Set groups can't depend on 'annotationsCommonMain' together as they belong to different Kotlin Source Set Trees.
          Source Sets from 'main' Tree:
        * 'jsMain'
        * 'wasmCommonMain'
      Source Sets from 'JUnit' Tree:
        * 'jvmJUnit'
      Source Sets from 'JUnit5' Tree:
        * 'jvmJUnit5'
      Source Sets from 'TestNG' Tree:
        * 'jvmTestNG'
      Solution: Please keep dependsOn edges only from one group and remove the others.
    """.trimIndent()

    private val INVALID_SOURCE_SET_DEPENDENCY_TEST_WARNING = """
      w: Invalid Source Set Dependency Across Trees
          Following Kotlin Source Set groups can't depend on 'commonTest' together as they belong to different Kotlin Source Set Trees.
          Source Sets from 'test' Tree:
        * 'jsTest'
        * 'jvmTest'
        * 'wasmJsTest'
        * 'wasmWasiTest'
      Source Sets from 'JUnit5Test' Tree:
        * 'jvmJUnit5Test'
      Source Sets from 'JUnitTest' Tree:
        * 'jvmJUnitTest'
      Source Sets from 'TestNGTest' Tree:
        * 'jvmTestNGTest'
      Solution: Please keep dependsOn edges only from one group and remove the others.
    """.trimIndent()

    private val UNSUPPORTED_COMPILE_ONLY_DEPENDENCIES_WARNING = """
      w: Unsupported `compileOnly` Dependencies in Kotlin Targets
      A compileOnly dependency is used in targets: Kotlin/JS.
      Dependencies:
          - org.jetbrains.kotlin:kotlin-stdlib:2.5.255-SNAPSHOT (source sets: jsMain)

      Using compileOnly dependencies in these targets is not currently supported, because compileOnly dependencies must be present during the compilation of projects that depend on this project.

      To ensure consistent compilation behaviour, compileOnly dependencies should be exposed as api dependencies.

      Example:

          kotlin {
              sourceSets {
                  nativeMain {
                      dependencies {
                          compileOnly("org.example:lib:1.2.3")
                          // additionally add the compileOnly dependency as an api dependency:
                          api("org.example:lib:1.2.3")
                      }
                  }
              }
          }

      This warning can be suppressed in gradle.properties:

          kotlin.suppressGradlePluginWarnings=IncorrectCompileOnlyDependencyWarning
      Solution: Please expose compileOnly dependencies as api dependencies.
    """.trimIndent()

    @JvmStatic
    fun testData() = shouldParse + shouldSkip

    private val shouldParse = listOf(
      TestCase(
        """
          |> Configure project :gradle-settings-conventions:something
          |$UNSUPPORTED_KOTLIN_PLUGIN_VERSION_WARNING
          |
          |> Task :gradle-settings-conventions:kotlin-daemon-config:checkKotlinGradlePluginConfigurationErrors SKIPPED
        """.trimMargin(),
        UNSUPPORTED_KOTLIN_PLUGIN_VERSION_WARNING.asWarning(),
      ),
      TestCase(
        """
          |> Configure project :gradle-build-conventions:analysis-api-artifact
          |$UNSUPPORTED_KOTLIN_PLUGIN_VERSION_WARNING
          |
          |$DEPRECATED_GRADLE_PROPERTY_WARNING
          |
          |> Configure project :gradle-build-conventions:android-sdk-provisioner
        """.trimMargin(),
        UNSUPPORTED_KOTLIN_PLUGIN_VERSION_WARNING.asWarning(),
        DEPRECATED_GRADLE_PROPERTY_WARNING.asWarning(),
      ),
      TestCase(
        """
          |$INTERNAL_KOTLIN_GRADLE_PLUGIN_PROPERTIES_WARNING
          |
          |
          |> Task :gradle-build-conventions:nodejs-configuration:checkKotlinGradlePluginConfigurationErrors SKIPPED
        """.trimMargin(),
        INTERNAL_KOTLIN_GRADLE_PLUGIN_PROPERTIES_WARNING.asWarning(),
      ),
      TestCase(
        """
          |> Configure project :kotlin-dom-api-compat
          |
          |$JS_ENVIRONMENT_NOT_SELECTED_WARNING
          |
          |> Task :gradle-build-conventions:asm-deprecating-transformer:checkKotlinGradlePluginConfigurationErrors SKIPPED
        """.trimMargin(),
        JS_ENVIRONMENT_NOT_SELECTED_WARNING.asWarning(),
      ),
      TestCase(
        """
          |> Configure project :kotlin-power-assert-runtime
          |
          |$DEFAULT_KOTLIN_HIERARCHY_TEMPLATE_WARNING
          |
          |$UNSUPPORTED_API_DEPENDENCY_TYPES_WARNING
          |
          |> Configure project :kotlin-scripting-ide-services-test
        """.trimMargin(),
        DEFAULT_KOTLIN_HIERARCHY_TEMPLATE_WARNING.asWarning(),
        UNSUPPORTED_API_DEPENDENCY_TYPES_WARNING.asWarning()
      ),
      TestCase(
        """
          |> Configure project :kotlin-test
          |
          |$INVALID_SOURCE_SET_DEPENDENCY_MAIN_WARNING
          |
          |$INVALID_SOURCE_SET_DEPENDENCY_TEST_WARNING
          |
          |> Configure project :kotlinx-atomicfu-runtime
        """.trimMargin(),
        INVALID_SOURCE_SET_DEPENDENCY_MAIN_WARNING.asWarning(),
        INVALID_SOURCE_SET_DEPENDENCY_TEST_WARNING.asWarning(),
      ),
      TestCase(
        """
          |> Configure project :kotlinx-atomicfu-runtime
          |
          |$UNSUPPORTED_COMPILE_ONLY_DEPENDENCIES_WARNING
          |
          |> Task :gradle-build-conventions:foreign-class-usage-checker:checkKotlinGradlePluginConfigurationErrors SKIPPED
        """.trimMargin(),
        UNSUPPORTED_COMPILE_ONLY_DEPENDENCIES_WARNING.asWarning(),
      ),
      TestCase(
        """
          |> Configure project :prepare:ide-plugin-dependencies:kotlin-jps-common-for-ide
          |
          |$UNSUPPORTED_API_DEPENDENCY_TYPES_WARNING
          |
          |BUILD SUCCESSFUL in 52s
          |229 actionable tasks: 229 up-to-date
        """.trimMargin(),
        UNSUPPORTED_API_DEPENDENCY_TYPES_WARNING.asWarning(),
      ),
      TestCase(
        DEPRECATED_GRADLE_PROPERTY_WARNING,
        DEPRECATED_GRADLE_PROPERTY_WARNING.asWarning(),
      ),
      TestCase(
        """
          |w: Some Kotlin Gradle Plugin Diagnostic
          |Some details.
          |
          |
          |Some unrelated output.
        """.trimMargin(),
        ("""
          |w: Some Kotlin Gradle Plugin Diagnostic
          |Some details.
        """.trimMargin()).asWarning(),
      ),
      TestCase(
        """
          |$KOTLIN_GRADLE_PLUGIN_ERROR
          |
          |FAILURE: Build failed with an exception.
        """.trimMargin(),
        KOTLIN_GRADLE_PLUGIN_ERROR.asError(),
      ),
    )

    private val shouldSkip = listOf(
      // this error should not be parsed as it contains a path associated to it
      TestCase(
        """
          |w: file:///project/build.gradle.kts:23:1: The expression is unused
          |e: /project/src/main/kotlin/A.kt: (7, 5): Unresolved reference: bbb
          |e: C:\project\src\main\kotlin\A.kt: (7, 5): Unresolved reference: bbb
          |e: [ksp] project-main/app/src/main/java/com/example/MainActivity.kt:39: AutoMigration Failure
        """.trimMargin()
      ),
      // this error should not be parsed as it does not start with a correct error kind
      TestCase(
        """
          |> Configure project :kotlin-compiler-embeddable
          |Removed :kotlin-compiler project dependency
          |warning: [options] bootstrap class path is not set in conjunction with -source 8
          |Warning: some text
          |w:
          |> Task :kotlin-stdlib:prepareKotlinIdeaImport UP-TO-DATE
          |
          |[Incubating] Problems report is available at: file:///project/build/reports/problems/problems-report.html
        """.trimMargin()
      ),
      // this error should not be parsed as the message ends before kotlin compiler message with file location
      TestCase(
        """
          |$DEPRECATED_GRADLE_PROPERTY_WARNING
          |e: /project/src/main/kotlin/A.kt: (7, 5): Unresolved reference: bbb
        """.trimMargin(),
        DEPRECATED_GRADLE_PROPERTY_WARNING.asWarning()
      )
    )

    private fun String.asWarning(): ExpectedEvent = asEvent(MessageEvent.Kind.WARNING)

    private fun String.asError(): ExpectedEvent = asEvent(MessageEvent.Kind.ERROR)

    private fun String.asEvent(kind: MessageEvent.Kind): ExpectedEvent {
      val rawTitle = lineSequence().first()
      val title = rawTitle.substringAfter(':').trim()
      val description = (listOf(title) + lines().drop(1)).joinToString("\n").trimEnd()
      return ExpectedEvent(kind, title, description)
    }
  }
}
