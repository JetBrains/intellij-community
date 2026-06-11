// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions.assertTrue
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertTrue


@GradleProjectTestApplication
@TestDataPath($$"$CONTENT_ROOT/testData")
@TestRoot("completion/kotlin/tests/testData")
@TestMetadata("buildGradleKts/dependencies")
internal class KotlinGradleDependenciesCompletionTest : AbstractKotlinGradleCompletionTest() {

  private val testCompletionService = object : DependencyCompletionService {
    override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
      return flowOf(
        DependencyCompletionEvent.Item(DependencyCompletionResult("myGroup1", "myArtifact1", "myVersion1", null, LOCAL)),
        DependencyCompletionEvent.Item(DependencyCompletionResult("myGroup2", "myArtifact2", "myVersion2", null, LOCAL)),
        DependencyCompletionEvent.Item(DependencyCompletionResult("myGroup3", "myArtifact3", "myVersion3", null, LOCAL)),
        DependencyCompletionEvent.Item(DependencyCompletionResult("fooGroup", "compileArtifact", "barVersion", null, LOCAL)),
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
  fun `test configuration completion on top level empty input with kotlin jvm plugin`(gradleVersion: GradleVersion) =
    verifyCompletion(gradleVersion)

  @ParameterizedTest
  @GradleTestSource(value = "8.0")
  @TestMetadata("configurationOnTopLevelEmptyInputBefore82.test")
  fun `test completion before 8,2 also shows configurations that couldn't be used in dependencies block`(gradleVersion: GradleVersion) =
    verifyCompletion(gradleVersion)

  @ParameterizedTest
  @BaseGradleVersionSource
  @TestMetadata("configurationOnTopLevelPartialInput.test")
  fun `test configuration completion on top level partial input`(gradleVersion: GradleVersion) = verifyCompletion(gradleVersion)

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test configuration without the accessor class is completed in quotes`(gradleVersion: GradleVersion) =
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val buildScriptFile = writeTextAndCommit(
        "build.gradle.kts", """
          val customSourceSet by sourceSets.registering {}
          dependencies {
              customSourceSet<caret>
          }
        """.trimIndent()
      )
      runInEdtAndWait {
        fixture.configureFromExistingVirtualFile(buildScriptFile)
        fixture.completeBasic()
        fixture.assertPreferredCompletionItems(0, "customSourceSetAnnotationProcessor", "customSourceSetApi")
        fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        fixture.checkResult(
          """
            val customSourceSet by sourceSets.registering {}
            dependencies {
                "customSourceSetAnnotationProcessor"(<caret>)
            }
          """.trimIndent()
        )
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource("false,true")
  fun `test standard configuration is completed without quotes`(gradleVersion: GradleVersion, runInDumbMode: Boolean) =
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
      val buildScriptFile = writeTextAndCommit(
        "build.gradle.kts", """
          dependencies {
              impl<caret>
          }
        """.trimIndent()
      )
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildScriptFile)
        codeInsightFixture.completeBasic()
        val implementationItem = codeInsightFixture.lookupElements
          ?.firstOrNull { it.lookupString == "implementation" }
        assertNotNull(implementationItem) { "Expected 'implementation' in the completion list" }
        codeInsightFixture.lookup.currentItem = implementationItem
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("""
          dependencies {
              implementation(<caret>)
          }
        """.trimIndent())
      }
    }

  @ParameterizedTest
  @BaseGradleVersionSource("""
    dependencies { dependencie<caret> } : dependencies,
    dependencies { inn<caret> } : inn
  """)
  fun `test second completion invocation shows unfiltered input`(
    gradleVersion: GradleVersion,
    expression: String,
    expectedSuggestion: String,
  ) {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val file = writeTextAndCommit("build.gradle.kts", expression)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        val invocationCount = 2
        codeInsightFixture.complete(CompletionType.BASIC, invocationCount)
        val lookupStrings = codeInsightFixture.lookupElementStrings
        assertNotNull(lookupStrings) {
          "Autocompletion was not expected (codeInsightFixture.lookupElementStrings returned null)"
        }
        Assertions.assertTrue(lookupStrings.contains(expectedSuggestion)) {
          "The completion was expected to contain `$expectedSuggestion`, but it doesn't. Actual suggestions: $lookupStrings"
        }
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test live templates are not shown if completion is not called at least twice`(gradleVersion: GradleVersion) {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, testRootDisposable)
    testJavaProject(gradleVersion) {
      val buildGradleKts = writeTextAndCommit("build.gradle.kts", "dependencies { i<caret> }")
      assertCompletionDoesntSuggest(
        buildGradleKts,
        unexpectedSuggestions = listOf("ifn", "inn", "interface", "iter")
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("false,true")
  fun `test scope argument - suggest version catalogs and Dependency-returning methods`(
    gradleVersion: GradleVersion,
    runInDumbMode: Boolean,
  ) {
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
      writeTextAndCommit(
        "gradle/libs.versions.toml", """ 
            [libraries]
            foo-bar-lib = { module = "org.junit.jupiter:junit-jupiter", version.ref = "6.0.0"  }
        """.trimIndent()
      )
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { implementation(<caret>) }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.assertPreferredCompletionItems(
          0,
          // catalog names are always on top
          "libs",
          // version catalog entries might be mixed with Dependency-returning methods, dependening on ML ranking
          "libs.foo.bar.lib",
          // methods returning Dependency
          "embeddedKotlin",
          "enforcedPlatform",
          "files",
          "fileTree",
          "gradleApi",
          "gradleTestKit",
          "kotlin",
          "platform",
          "project",
          "testFixtures",
          "variantOf"
        )
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test scope argument - when quoted, don't suggest Dependency-returning methods`(gradleVersion: GradleVersion) {
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val file = writeTextAndCommit("build.gradle.kts", """dependencies { implementation("<caret>") }""")
      assertCompletionDoesntSuggest(
        file,
        unexpectedSuggestions = listOf(
          "platform", "enforcedPlatform", "project", "kotlin", "embeddedKotlin", "testFixtures",
          "files", "fileTree", "variantOf", "gradleApi", "gradleTestKit"
        )
      )
    }
  }

  @ParameterizedTest(name = "[{index}] {0}, {1}({2}) -> {1}({3})")
  @BaseGradleVersionSource(
    """
      implementation, 
      \"implementation\"
    """,
    """
      platfo<caret>           : platform(<caret>),
      enforcedPlatfo<caret>   : enforcedPlatform(<caret>),
      proje<caret>            : project(<caret>),
      kotl<caret>             : kotlin(<caret>),
      embeddedKotl<caret>     : embeddedKotlin(<caret>),
      testFixtur<caret>       : testFixtures(<caret>),
      fil<caret>              : files(<caret>),
      fileTr<caret>           : fileTree(<caret>),
      variant<caret>          : variantOf(<caret>),
      gradleA<caret>          : gradleApi()<caret>,
      gradleTe<caret>         : gradleTestKit()<caret>
    """
  )
  fun `test scope argument - complete Dependency-returning method with args and put caret inside brackets`(
    gradleVersion: GradleVersion,
    configuration: String,
    input: String,
    expected: String,
  ) {
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { $configuration($input) }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        val isAutocompleted = codeInsightFixture.completeBasic() == null
        if (!isAutocompleted) {
          codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        }
        codeInsightFixture.checkResult("dependencies { $configuration($expected) }")
      }
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
  @BaseGradleVersionSource
  @TestMetadata("versionCatalogs/quotedScopeArgument")
  fun `test version catalog completion in an argument of a quoted scope`(gradleVersion: GradleVersion) =
    verifyCompletion(gradleVersion)

  @ParameterizedTest
  @BaseGradleVersionSource(""" 
    implementation(platform(li<caret>)),
    implementation(enforcedPlatform(li<caret>)),
    testImplementation(testFixtures(li<caret>)),
    implementation(variantOf(li<caret>)),
    "customSourceSetImplementation"(variantOf(li<caret>))
  """, "false,true")
  fun `test version catalog completion in allowed DependencyHandler methods`(
    gradleVersion: GradleVersion,
    expression: String,
    runInDumbMode: Boolean,
  ) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE, runInDumbMode) {
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
  @BaseGradleVersionSource(""" 
    implementation(project(li<caret>)),
    implementation(files(li<caret>)) 
  """, "false,true")
  fun `test version catalogs are not suggested in inapplicable methods`(
    gradleVersion: GradleVersion,
    expression: String,
    runInDumbMode: Boolean,
  ) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE, runInDumbMode) {
      val buildScript = writeTextAndCommit("build.gradle.kts", "dependencies { $expression }")
      writeTextAndCommit(
        "gradle/libs.versions.toml",
        """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          [bundles]
          my-bundle-aaa = ["my-library-aaa"]
        """.trimIndent()
      )
      assertCompletionDoesntSuggest(buildScript, listOf("libs", "libs.bundles.my.bundle.aaa", "libs.my.library.aaa"))
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
  @BaseGradleVersionSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS, "false,true")
  fun `test coordinates completion in dependency configuration`(
    gradleVersion: GradleVersion,
    dependencyConfigurationEscaped: String,
    runInDumbMode: Boolean,
  ) {
    val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
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
    implementation("<caret>myGroup1<colon>myArtifact1<colon>myVersion1"),
    implementation("myGr<caret>something"),
    implementation("myGroup1<colon>myArti<caret><colon><colon>"),
    implementation("myGroup1<colon>myArtifact1<colon>myVer<caret>si")
  """)
  fun `test coordinates completion when caret is in the middle of the dependency string`(
    gradleVersion: GradleVersion,
    dependencyEntryEscaped: String,
  ) {
    val dependencyEntry = dependencyEntryEscaped.unescape()
    val dependencyCompletionResult = "implementation(\"myGroup1:myArtifact1:myVersion1\")"

    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
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
  @BaseGradleVersionSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS, "false,true")
  fun `test coordinates completion configuration names`(
    gradleVersion: GradleVersion,
    dependencyConfigurationEscaped: String,
    runInDumbMode: Boolean,
  ) {
    val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
    val substitutionResult = if (dependencyConfiguration.contains(",")) {
      val items = dependencyConfiguration.split(",")
      "${items[0]}(${items[1]}(\"g:a:v\"))"
    }
    else "$dependencyConfiguration(\"g:a:v\")"

    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "g",
            "a",
            "v",
            dependencyConfiguration,
            source = LOCAL
          )),
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "g",
            "a",
            "v2",
            dependencyConfiguration,
            source = LOCAL
          )),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
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
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.example.p",
            "my-long-artifact-id",
            "2.7.0",
            source = LOCAL
          )),
          DependencyCompletionEvent.Item(DependencyCompletionResult(
            "org.example.p",
            "my-long-artifact-id",
            "2.7.1",
            source = LOCAL
          )),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
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
      override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("g", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("h", source = LOCAL))
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
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
      override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("a", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("b", source = LOCAL))
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
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


  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test K2 Contributor is not ignored in unsupported cases`(gradleVersion: GradleVersion) {
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val file = writeTextAndCommit(
        "build.gradle.kts",
        """
          dependencies { 
            implementation("org.junit.jupiter:junit-jupiter:6.0.0") {
              ex<caret>
            }
          }
        """.trimIndent()
      )
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        val expectedElement = codeInsightFixture.lookupElements?.find {
          it.contributorClass?.name == "org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2CallableCompletionContributor"
          && it.lookupString == "exclude"
        }
        assertNotNull(expectedElement, "Expected to find an element produced by K2 Completion Contributor, but it wasn't found")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test command completion is not shown in the dependencies block`(gradleVersion: GradleVersion) {
    Registry.get("ide.completion.command.force.enabled").setValue(true, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val file = writeTextAndCommit(
        "build.gradle.kts", """
          dependencies { 
              implementation("org.junit.jupiter:junit-jupiter:6.0.0").<caret>
          }
        """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        val commandCompletionExamples = listOf("Surround with 'try / finally'", "Extract function", "Go to members", "Reformat code")
        val unexpectedLookup = codeInsightFixture.completeBasic()
          ?.map { it.lookupString }
          ?.filter { it in commandCompletionExamples }
        assertTrue(unexpectedLookup.isNullOrEmpty()) {
          "The command completion was not expected, but these commands were suggested: \n${unexpectedLookup}"
        }
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test command completion is not disabled outside the dependencies block`(gradleVersion: GradleVersion) {
    Registry.get("ide.completion.command.force.enabled").setValue(true, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE) {
      val file = writeTextAndCommit("build.gradle.kts", "{}.refor<caret>")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        val lookup = codeInsightFixture.completeBasic()?.map { it.lookupString }
        assertTrue(lookup?.any { it == "Reformat code" } == true) {
          "The command completion was expected outside the dependencies block, but it wasn't suggested. Actual lookup: $lookup"
        }
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("""
    implementation(kotlin("std<caret>")),
    implementation(kotlin("<caret>")),
    implementation(kotlin(module="std<caret>")),
    implementation(kotlin(module="<caret>"))
  """, "false,true")
  fun `test kotlin shortcut module completion`(gradleVersion: GradleVersion, completionEscaped: String, runInDumbMode: Boolean) {
    val completion = completionEscaped.unescape()
    val completionResult = completion.replace(Regex("\"[^\"]*<caret>\""), "\"stdlib:2.0.21\"")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyCompletionResult("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyCompletionResult("org.jetbrains.kotlin", "kotlin-reflect", "2.0.21", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyCompletionResult("org.example", "unrelated", "1.0", source = LOCAL)),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.assertPreferredCompletionItems(0, "stdlib:2.0.21", "reflect:2.0.21")
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult("dependencies { $completionResult }")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("""
    implementation(embeddedKotlin("std<caret>")),
    implementation(embeddedKotlin("<caret>")),
    implementation(embeddedKotlin(module="std<caret>")),
    implementation(embeddedKotlin(module="<caret>"))
  """, "false,true")
  fun `test embeddedKotlin shortcut module completion`(
    gradleVersion: GradleVersion,
    completionEscaped: String,
    runInDumbMode: Boolean,
  ) {
    val completion = completionEscaped.unescape()
    // `embeddedKotlin` accepts no version argument, so completion must produce only the module name.
    val completionResult = completion.replace(Regex("\"[^\"]*<caret>\""), "\"stdlib\"")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("kotlin-stdlib", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("kotlin-reflect", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("unrelated", source = LOCAL)),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.assertPreferredCompletionItems(0, "stdlib", "reflect")
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult("dependencies { $completionResult }")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("""
    implementation(kotlin("stdlib"<comma> "1.0<caret>")),
    implementation(kotlin("stdlib"<comma> "<caret>")),
    implementation(kotlin(module="stdlib"<comma> version="<caret>")),
    implementation(kotlin(version="<caret>"<comma> module="stdlib"))
  """, "false,true")
  fun `test kotlin shortcut version completion`(gradleVersion: GradleVersion, completionEscaped: String, runInDumbMode: Boolean) {
    val completion = completionEscaped.unescape()
    val completionResult = completion.replace(Regex("\"[^\"]*<caret>\""), "\"2.0.21\"")
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("2.0.21", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("1.9.24", source = LOCAL)),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        codeInsightFixture.assertPreferredCompletionItems(0, "2.0.21", "1.9.24")
        codeInsightFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        codeInsightFixture.checkResult("dependencies { $completionResult }")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("""
    implementation(embeddedKotlin("stdlib"<comma> "1.0<caret>")),
    implementation(embeddedKotlin("stdlib"<comma> "<caret>")),
    implementation(embeddedKotlin(module="stdlib"<comma> version="<caret>")),
    implementation(embeddedKotlin(version="<caret>"<comma> module="stdlib"))
  """, "false,true")
  fun `test embeddedKotlin does not offer version completion`(
    gradleVersion: GradleVersion,
    completionEscaped: String,
    runInDumbMode: Boolean,
  ) {
    val completion = completionEscaped.unescape()
    application.replaceService(DependencyCompletionService::class.java, object : DependencyCompletionService {
      override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
        return flowOf(
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("2.0.21", source = LOCAL)),
          DependencyCompletionEvent.Item(DependencyPartCompletionResult("1.9.24", source = LOCAL)),
        )
      }
    }, testRootDisposable)
    test(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE, runInDumbMode) {
      val file = writeTextAndCommit("build.gradle.kts", "dependencies { $completion }")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        codeInsightFixture.completeBasic()
        // `embeddedKotlin` accepts no version argument, so no version suggestions should be produced.
        val lookupStrings = codeInsightFixture.lookupElementStrings.orEmpty()
        assertTrue(lookupStrings.none { it == "2.0.21" || it == "1.9.24" }) {
          "Expected no version completions for embeddedKotlin's second argument, but got: $lookupStrings"
        }
      }
    }
  }

  private fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, runInDumbMode: Boolean, test: () -> Unit) {
    test(gradleVersion, fixtureBuilder) {
      if (runInDumbMode) {
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
          test()
        }
      }
      else {
        test()
      }
    }
  }

  private fun verifyVersionCatalogCompletion(gradleVersion: GradleVersion) {
    verifyCompletion(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
  }

  private fun verifyCompletion(gradleVersion: GradleVersion) {
    verifyCompletion(gradleVersion, KOTLIN_GRADLE_COMPLETION_FIXTURE)
  }

  private fun String.unescape(): String = this
    .replace("<colon>", ":")
    .replace("<comma>", ",")

  private fun assertCompletionDoesntSuggest(file: VirtualFile, unexpectedSuggestions: List<String>) = runInEdtAndWait {
    codeInsightFixture.configureFromExistingVirtualFile(file)
    val contentBeforeCompletion = codeInsightFixture.file.text

    codeInsightFixture.completeBasic()
    val suggestions = codeInsightFixture.lookupElementStrings
    if (suggestions == null) {
      // check that nothing was autocompleted
      codeInsightFixture.checkResult(contentBeforeCompletion)
    } else {
      val filtered = suggestions.filter { it in unexpectedSuggestions }
      assertTrue(filtered.isEmpty(), "The completion lookup contains unexpected suggestions: $filtered")
    }
  }

  companion object {
    private const val DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS = """
      api,
      implementation,
      compileOnly,
      runtimeOnly,
      testImplementation,
      testCompileOnly,
      testRuntimeOnly,
      implementation<colon>platform,
      implementation<colon>enforcedPlatform,
      implementation<colon>testFixtures,
      \"customSourceSetImplementation\"
    """

    private val KOTLIN_GRADLE_COMPLETION_FIXTURE =
      GradleTestFixtureBuilder.create("KotlinGradleDependenciesCompletionTest") { gradleVersion ->
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
          withKotlinJvmPlugin()
          withPrefix { code("val customSourceSet by sourceSets.creating {}") }
        }
        withFile("gradle/libs.versions.toml", "")
      }
  }
}