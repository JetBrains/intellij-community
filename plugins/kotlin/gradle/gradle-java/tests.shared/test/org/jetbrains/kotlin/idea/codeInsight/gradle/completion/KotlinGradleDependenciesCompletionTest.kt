// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.idea.completion.api.*
import org.jetbrains.kotlin.gradle.scripting.shared.completion.KotlinGradleScriptCompletionContributor
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest

@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/completion/buildGradleKts/dependencies")
abstract class KotlinGradleDependenciesCompletionTest: AbstractKotlinGradleCompletionTest() {

    private val testCompletionService = object : DependencyCompletionService {
        override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
            return flowOf(
                DependencyCompletionResult("myGroup1", "myArtifact1", "myVersion1"),
                DependencyCompletionResult("myGroup2", "myArtifact2", "myVersion2"),
                DependencyCompletionResult("myGroup3", "myArtifact3", "myVersion3"),
                DependencyCompletionResult("fooGroup", "compileArtifact", "barVersion"),
            )
        }
    }

    @BeforeEach
    fun `replace completion service`() {
        application.replaceService(DependencyCompletionService::class.java, testCompletionService, testRootDisposable)
        removeOtherCompletionContributors()
    }

    @ParameterizedTest
    @BaseGradleVersionSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS)
    fun `test coordinates completion in dependency configuration`(gradleVersion: GradleVersion, dependencyConfigurationEscaped: String) {
        val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            val parts = dependencyConfiguration.split(":")

            val dependencyEntryTemplate = if (parts.size > 1)
                "${parts[0]}(${parts[1]}(\"<>\"))"
            else
                "$dependencyConfiguration(\"<>\")"

            val dependencyEntry = dependencyEntryTemplate.replace("<>", "myGrou<caret>")
            val dependencyCompletionResult = dependencyEntryTemplate.replace("<>", "myGroup1:myArtifact1:myVersion1")

            val file = writeTextAndCommit("build.gradle.kts", "dependencies { $dependencyEntry }")
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(file)
                codeInsightFixture.completeBasic()
                codeInsightFixture.assertPreferredCompletionItems(
                    0, "myGroup1:myArtifact1:myVersion1",
                    "myGroup2:myArtifact2:myVersion2",
                    "myGroup3:myArtifact3:myVersion3",
                )
                codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { $dependencyCompletionResult }")
            }
        }
    }

    @Disabled("server-side completion only")
    @ParameterizedTest
    @BaseGradleVersionSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS)
    fun `test coordinates completion configuration names`(gradleVersion: GradleVersion, dependencyConfigurationEscaped: String) {
        val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
        val substitutionResult = if (dependencyConfiguration.contains(":")) {
            val items = dependencyConfiguration.split(":")
            "${items[0]}(${items[1]}(\"g:a:v\"))"
        } else "$dependencyConfiguration(\"g:a:v\")"

        application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
            override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
                return flowOf(
                    DependencyCompletionResult("g", "a", "v", dependencyConfiguration),
                    DependencyCompletionResult("g", "a", "v2", dependencyConfiguration),
                )
            }
        }, testRootDisposable)
        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            val file = writeTextAndCommit("build.gradle.kts", "dependencies { a<caret> }")
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(file)
                codeInsightFixture.completeBasic()
                codeInsightFixture.lookup.currentItem =
                    codeInsightFixture.lookupElements!!.find { it.lookupString.contains(substitutionResult) }
                codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { $substitutionResult }")
            }
        }
    }

    @Disabled("server-side completion only")
    @ParameterizedTest
    @BaseGradleVersionSource("""
            org.example.p<colon>my-long-artifact-id<colon>2.7.<caret>,
            org.example.p<colon>my-long-artifact-id<colon><caret>,
            org.example.p<colon>my-long-artifact-id<caret>,
            org.example.p<colon>my-long-artifact-<caret>,
            org.example.p<colon>my-l<caret>,
            org.example.p<colon><caret>,
            org.example.p<caret>,
            org.example.<caret>,
            org.<caret>,
            my-long-artifact-id<colon>2.7.<caret>,
            my-long-artifact-id<colon><caret>,
            my-long-artifact-id<caret>,
            my-long-artifact-<caret>,
            my-<caret>
        """)
    fun `test coordinates completion in dependencies`(gradleVersion: GradleVersion, completionEscaped: String) {
        val completion = completionEscaped.unescape()
        application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
            override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
                return flowOf(
                    DependencyCompletionResult("org.example.p", "my-long-artifact-id", "2.7.0"),
                    DependencyCompletionResult("org.example.p", "my-long-artifact-id", "2.7.1"),
                )
            }
        }, testRootDisposable)
        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(file)
                codeInsightFixture.completeBasic()
                codeInsightFixture.lookup.currentItem =
                    codeInsightFixture.lookupElements!!.find {
                        it.lookupString.contains("implementation(\"org.example.p:my-long-artifact-id:2.7.0\")")
                    }
                codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { implementation(\"org.example.p:my-long-artifact-id:2.7.0\") }")
            }
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource("""
            implementation(group="<caret>"),
            implementation(group="<caret>"<comma> name="a"),
            implementation(group="<caret>"<comma> name="a"<comma> version=""),
            implementation(name="a"<comma> group="<caret>"),
            implementation(name="a"<comma> group="<caret>"<comma> version=""),
            implementation(version=""<comma> name="a"<comma> group="<caret>"),
            implementation(configuration=""<comma> classifier=""<comma> ext =""<comma> version=""<comma> name="a"<comma> group="<caret>"),
            implementation("g:a:v") { exclude("<caret>") },
            implementation("g:a:v") { exclude(group="<caret>") },
            implementation("g:a:v") { exclude(module="a"<comma> group="<caret>") }
        """)
    fun `test group completion in dependencies`(gradleVersion: GradleVersion, completionEscaped: String) {
        val completion = completionEscaped.unescape()
        val completionResult = completion.replace("<caret>", "g")
        application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
            override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<String> {
                return flowOf("g", "h")
            }
        }, testRootDisposable)
        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(file)
                codeInsightFixture.completeBasic()
                codeInsightFixture.lookup.currentItem =
                    codeInsightFixture.lookupElements!!.find { it.lookupString.contains("g") }
                codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { $completionResult }")
            }
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource("""
            implementation(name="<caret>"),
            implementation(name="<caret>"<comma> group="g"),
            implementation(name="<caret>"<comma> group="g"<comma> version=""),
            implementation(group="g"<comma> name="<caret>"),
            implementation(group="g"<comma> name="<caret>"<comma> version=""),
            implementation(version=""<comma> group="g"<comma> name="<caret>"),
            implementation(configuration=""<comma> classifier=""<comma> ext =""<comma> version=""<comma> name="<caret>"<comma> group="g"),
            implementation("g:a:v") { exclude(module="<caret>") },
            implementation("g:a:v") { exclude(group="g"<comma> module="<caret>") }
        """)
    fun `test artifact completion in dependencies`(gradleVersion: GradleVersion, completionEscaped: String) {
        val completion = completionEscaped.unescape()
        val completionResult = completion.replace("<caret>", "a")
        application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
            override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<String> {
                return flowOf("a", "b")
            }
        }, testRootDisposable)
        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(file)
                codeInsightFixture.completeBasic()
                codeInsightFixture.lookup.currentItem =
                    codeInsightFixture.lookupElements!!.find { it.lookupString.contains("a") }
                codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { $completionResult }")
            }
        }
    }

    @Disabled("server-side completion only")
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("artifactCompletionOnTopLevel.test")
    fun testArtifactCompletionOnTopLevel(gradleVersion: GradleVersion) = verifyCompletion(gradleVersion)

    private fun verifyCompletion(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT)
    }

    private fun String.unescape(): String = this
        .replace("<colon>", ":")
        .replace("<comma>", ",")

    private fun removeOtherCompletionContributors() {
        val pluginDescriptor = DefaultPluginDescriptor("registerCompletionContributor")
        val contributor =
            CompletionContributorEP("any", KotlinGradleScriptCompletionContributor::class.java.getName(), pluginDescriptor)
        ExtensionTestUtil.maskExtensions(
            CompletionContributor.EP,
            listOf(contributor),
            testRootDisposable
        )
    }

    companion object {
        const val DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS = """
            api,
            implementation,
            compileOnly,
            compileOnlyApi,
            runtimeOnly,
            testImplementation,
            testCompileOnly,
            testRuntimeOnly,
            implementation<colon>platform,
            implementation<colon>enforcedPlatform
        """
    }
}