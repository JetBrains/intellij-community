// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testFramework.gradle

import com.intellij.openapi.util.Couple
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.execution.GradleDebuggingIntegrationTestCase
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class KotlinGradleDebuggingIntegrationTest : GradleDebuggingIntegrationTestCase() {
    @Test
    @TargetVersions("6.1+") // this test fails on Gradle 6.0 because of the bug
    // it resolves dependency org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 as sources jar
    fun `coroutines debugging setup works`() = testWithJUnitAndJavaExec()

    @Test
    @TargetVersions("8.1+") // KTIJ-26064 became a problem since Gradle 8.1
    fun `coroutines debugging setup doesn't break configuration cache`() = testWithJUnitAndJavaExec(listOf("--configuration-cache"))

    private fun testWithJUnitAndJavaExec(scriptParameters: List<String> = emptyList()) {
        val testArgsFile = createArgsFile(name = "testArgs.txt")
        val execArgsFile = createArgsFile(name = "execArgs.txt")
        importProject {
            withKotlinJvmPlugin()
            withPrintArgsTask(execArgsFile)
            withJUnit5()
            addImplementationDependency(COROUTINES_DEPENDENCY)
            configureTestTask {
                call("dependsOn", "printArgs")
                call("systemProperty", "idea.test.argsFile", testArgsFile.absolutePath)
            }
        }
        createPrintArgsClass()
        createProjectSubFile(
            "src/test/java/Tests.java",
            //language=java
            """
            import org.junit.jupiter.api.Test;                

            class Tests {
              @Test
              void test() {
                pack.AClass.main(new String[] { System.getProperty("idea.test.argsFile") });
              }
            }
            """.trimIndent()
        )
        ensureDeleted(execArgsFile)
        ensureDeleted(testArgsFile)
        val mergedScriptParameters = if (isGradleAtLeast("8.2")) {
            scriptParameters + "--warning-mode=summary" // KGP causes deprecation warnings
        } else {
            scriptParameters
        }
        val output = executeRunConfiguration(":test", isDebugServerProcess = true, scriptParameters = mergedScriptParameters.joinToString(" "))
        assertDebugJvmArgs(":printArgs", execArgsFile, shouldBeDebugged = false)
        assertDebugJvmArgs(":test", testArgsFile)
        val testArgsFileAssertion = assertThat(testArgsFile).content()
        testArgsFileAssertion
            .describedAs("test run should contain java agent setup")
            .containsPattern(JAVA_AGENT_PATTERN)
        testArgsFileAssertion
            .describedAs("test run should enable assertions")
            .contains(ENABLE_ASSERTIONS_FLAG)
        assertThat(output)
            .describedAs("Build should be successful")
            .contains("BUILD SUCCESSFUL")
    }

    override fun handleDeprecationError(errorInfo: Couple<String>?) {
        if (errorInfo != null && isGradleAtLeast("8.2")) {
            assertThat(errorInfo.second)
                .describedAs("JavaPluginConvention usage causes deprecation warnings during import since Gradle 8.2. If it's not a case anymore, remove this workaround")
                .isEqualTo("The org.gradle.api.plugins.JavaPluginConvention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/8.3/userguide/upgrading_version_8.html#java_convention_deprecation\n" +
                                   "\tat org.gradle.api.plugins.internal.NaggingJavaPluginConvention.logDeprecation(NaggingJavaPluginConvention.java:185)\n" +
                                   "\tat org.gradle.api.plugins.internal.NaggingJavaPluginConvention.getSourceSets(NaggingJavaPluginConvention.java:154)\n" +
                                   "\tat org.jetbrains.kotlin.idea.gradleTooling.KotlinTasksPropertyUtilsKt.getPureKotlinSourceRoots(KotlinTasksPropertyUtils.kt:59)\n" +
                                   "\tat org.jetbrains.kotlin.idea.gradleTooling.KotlinTasksPropertyUtilsKt.getKotlinTaskProperties(KotlinTasksPropertyUtils.kt:90)\n" +
                                   "\tat org.jetbrains.kotlin.idea.gradleTooling.KotlinTasksPropertyUtilsKt.acknowledgeTask(KotlinTasksPropertyUtils.kt:83)\n" +
                                   "\tat org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModelBuilder.buildAll(KotlinGradleModelBuilder.kt:240)\n" +
                                   "\tat org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModelBuilder.buildAll(KotlinGradleModelBuilder.kt:206)")
            return
        }
        super.handleDeprecationError(errorInfo)
    }

    companion object {
        private const val COROUTINES_DEPENDENCY = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
        private const val JAVA_AGENT_PATTERN = "-javaagent:((?!\\.jar).)*kotlinx-coroutines-core-(jvm-)?1\\.7\\.3\\.jar"
        private const val ENABLE_ASSERTIONS_FLAG = "-ea"
    }
}