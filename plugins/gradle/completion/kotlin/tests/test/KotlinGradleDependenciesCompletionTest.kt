// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertTrue

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestDataPath($$"$CONTENT_ROOT/testData")
@TestRoot("completion/kotlin/tests/testData")
@TestMetadata("buildGradleKts/dependencies")
internal class KotlinGradleDependenciesCompletionTest: AbstractKotlinGradleCompletionTest() {

    private val testCompletionService = object : DependencyCompletionService {
        override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
            return flowOf(
              DependencyCompletionResult("myGroup1", "myArtifact1", "myVersion1", null,DependencyCompletionContributionSource.LOCAL),
              DependencyCompletionResult("myGroup2", "myArtifact2", "myVersion2", null, DependencyCompletionContributionSource.LOCAL),
              DependencyCompletionResult("myGroup3", "myArtifact3", "myVersion3", null, DependencyCompletionContributionSource.LOCAL),
              DependencyCompletionResult("fooGroup", "compileArtifact", "barVersion", null, DependencyCompletionContributionSource.LOCAL),
            )
        }
    }

    @BeforeEach
    fun `replace completion service`() {
        application.replaceService(DependencyCompletionService::class.java, testCompletionService, testRootDisposable)
    }

    // This list of configurations with `isCanBeDeclared == true`, coming from the Kotlin JVM plugin, also contains internal configurations
    // that should not be exposed to the user. Adjust the test when KT-83780 is fixed.
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("configurationOnTopLevelEmptyInputKotlinJVM.test")
    fun `test configuration completion on top level empty input with kotlin jvm plugin`(gradleVersion: GradleVersion) = verifyCompletion(gradleVersion)

    @ParameterizedTest
    @GradleTestSource(value = "8.0")
    @TestMetadata("configurationOnTopLevelEmptyInputBefore82.test")
    fun `test completion before 8,2 also shows configurations that couldn't be used in dependencies block`(gradleVersion: GradleVersion) = verifyCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("configurationOnTopLevelPartialInput.test")
    fun `test configuration completion on top level partial input`(gradleVersion: GradleVersion) = verifyCompletion(gradleVersion)

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test second completion invocation shows unfiltered input`(gradleVersion: GradleVersion) =
    test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { cla<caret> }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        repeat(times = 2) {
          codeInsightFixture.completeBasic()
        }
        assertTrue { codeInsightFixture.lookupElementStrings!!.contains("class") }
      }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentEmptyInput")
    fun `test completion in a scope argument for an empty input`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentCatalogNames")
    fun `test catalog name completion in a scope argument`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentLibraries")
    fun `test library entry completion in a scope argument`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentLibrariesFromCustomCatalog")
    fun `test library entry completion in a scope argument for a custom catalog`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentBundles")
    fun `test bundle entry completion in a scope argument`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentBundlesWhenSectionNotSpecified")
    fun `test bundle entry completion when section is not specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/scopeArgumentWhenCatalogNameNotSpecified")
    fun `test libraries and bundles from multiple catalogs completion when a catalog name is not specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource(
        """
        implementation(platform(li<caret>)),
        implementation(enforcedPlatform(li<caret>)),
        testImplementation(testFixtures(li<caret>)),
        implementation(variantOf(li<caret>))
        """
    )
    fun `test version catalog completion in allowed DependencyHandler methods`(gradleVersion: GradleVersion, expression: String) {
        test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
            val buildScript = writeTextAndCommit("build.gradle.kts", "dependencies { $expression }")
            writeTextAndCommit(
                "gradle/libs.versions.toml", """
                [versions]
                my-version = "1.0.0"
                [plugins]
                my-plugin = "my.plugin:1.0.0"
                [libraries]
                my-library-aaa = "com.example:my-library-aaa:1.0.0"
                [bundles]
                my-bundle-aaa = ["my-library-aaa"]
            """.trimIndent()
            )
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(buildScript)
                codeInsightFixture.completeBasic()
                codeInsightFixture.assertPreferredCompletionItems(
                    0,
                    "libs",
                    "customLibs",
                    "libs.bundles.my.bundle.aaa",
                    "libs.my.library.aaa"
                )
                codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { ${expression.replace("li<caret>", "libs")} }")
            }
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource(
        """ 
        implementation(project(li<caret>)),
        implementation(files(li<caret>)) 
        """
    )
    fun `test version catalogs are not suggested in inapplicable methods`(gradleVersion: GradleVersion, expression: String) {
        test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
            val buildScript = writeTextAndCommit("build.gradle.kts", "dependencies { $expression }")
            writeTextAndCommit(
                "gradle/libs.versions.toml", """
                [libraries]
                my-library-aaa = "com.example:my-library-aaa:1.0.0"
                [bundles]
                my-bundle-aaa = ["my-library-aaa"]
            """.trimIndent()
            )
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(buildScript)
                codeInsightFixture.completeBasic()
                assertTrue("No completion suggestions expected") {
                    codeInsightFixture.lookupElements.isNullOrEmpty()
                }
                codeInsightFixture.checkResult("dependencies { $expression }")
            }
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/pluginsAndVersionsAreNotCompletedWhenNoSection")
    fun `test plugins and versions are not completed in when the section is not specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/versionsAreCompletedWhenSectionIsSpecified")
    fun `test versions are completed in when the section is specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

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

    @ParameterizedTest
    @BaseGradleVersionSource("""
            impl("<caret>myGroup1<colon>myArtifact1<colon>myVersion1"),
            impl("myGr<caret>something"),
            impl("myGroup1<colon>myArti<caret><colon><colon>"),
            impl("myGroup1<colon>myArtifact1<colon>myVer<caret>si")
        """)
    fun `test coordinates completion when caret is in the middle of the dependency string`(
        gradleVersion: GradleVersion,
        dependencyEntryEscaped: String,
    ) {
        val dependencyEntry = dependencyEntryEscaped.unescape()
        val dependencyCompletionResult = "impl(\"myGroup1:myArtifact1:myVersion1\")"

        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            val file = writeTextAndCommit("build.gradle.kts", "dependencies { $dependencyEntry }")
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(file)
                codeInsightFixture.completeBasic()
                codeInsightFixture.assertPreferredCompletionItems(
                    0, "myGroup1:myArtifact1:myVersion1",
                    "myGroup2:myArtifact2:myVersion2",
                    "myGroup3:myArtifact3:myVersion3",
                )
                codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
                codeInsightFixture.checkResult("dependencies { $dependencyCompletionResult }")
            }
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS)
    fun `test coordinates completion configuration names`(gradleVersion: GradleVersion, dependencyConfigurationEscaped: String) {
        val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
        val substitutionResult = if (dependencyConfiguration.contains(",")) {
            val items = dependencyConfiguration.split(",")
            "${items[0]}(${items[1]}(\"g:a:v\"))"
        } else "$dependencyConfiguration(\"g:a:v\")"

        application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
            override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> {
                return flowOf(
                    DependencyCompletionResult(
                        "g",
                        "a",
                        "v",
                        dependencyConfiguration,
                        source = DependencyCompletionContributionSource.LOCAL
                    ),
                    DependencyCompletionResult(
                        "g",
                        "a",
                        "v2",
                        dependencyConfiguration,
                        source = DependencyCompletionContributionSource.LOCAL
                    ),
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
                    DependencyCompletionResult(
                        "org.example.p",
                        "my-long-artifact-id",
                        "2.7.0",
                        source = DependencyCompletionContributionSource.LOCAL
                    ),
                    DependencyCompletionResult(
                        "org.example.p",
                        "my-long-artifact-id",
                        "2.7.1",
                        source = DependencyCompletionContributionSource.LOCAL
                    ),
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
            override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyPartCompletionResult> {
                return flowOf(
                    DependencyPartCompletionResult("g", source = DependencyCompletionContributionSource.LOCAL),
                    DependencyPartCompletionResult("h", source = DependencyCompletionContributionSource.LOCAL)
                )
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
            override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyPartCompletionResult> {
                return flowOf(
                  DependencyPartCompletionResult("a", source = DependencyCompletionContributionSource.LOCAL),
                  DependencyPartCompletionResult("b", source = DependencyCompletionContributionSource.LOCAL)
                )
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

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("artifactCompletionOnTopLevel.test")
    fun testArtifactCompletionOnTopLevel(gradleVersion: GradleVersion) = verifyCompletion(gradleVersion)

    private fun verifyVersionCatalogCompletion(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    private fun verifyCompletion(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion, KOTLIN_JVM_PROJECT)
    }

    private fun String.unescape(): String = this
        .replace("<colon>", ":")
        .replace("<comma>", ",")

    companion object {
        const val DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS = """
            api,
            implementation,
            compileOnly,
            runtimeOnly,
            testImplementation,
            testCompileOnly,
            testRuntimeOnly,
            implementation<colon>platform,
            implementation<colon>enforcedPlatform,
            implementation<colon>testFixtures
        """

        val KOTLIN_JVM_PROJECT: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("kotlin-jvm-project") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("kotlin-jvm-project")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin()
            }
            withFile("gradle/libs.versions.toml", "")
        }
    }
}