// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.tests.integration

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.gradle.codeInsight.kotlin.testFramework.completeGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.configureGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.versionCatalogsGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.annotations.CsvCrossProductSource
import com.intellij.gradle.testFramework.annotations.GradleTestSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.util.writeTextAndCommit
import com.intellij.idea.IJIgnore
import com.intellij.openapi.util.registry.Registry
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

@IJIgnore(issue = "KT-88180")
@GradleImmutableProjectTestApplication
internal class KotlinGradleDependenciesCompletionTest {

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

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  @Nested
  @ParameterizedClass
  @BaseGradleVersionSource
  inner class BaseGradleVersion(gradleVersion: GradleVersion) {

    private val codeInsightFixture by versionCatalogsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun `replace completion service`() {
      codeInsightFixture.replaceCompletionService(testCompletionService)
    }

    // This list of configurations with `isCanBeDeclared == true`, coming from the Kotlin JVM plugin, also contains internal configurations
    // that should not be exposed to the user. Adjust the test when KT-83780 is fixed.
    @Test
    fun `test configuration completion on top level empty input with kotlin jvm plugin`() {
      codeInsightFixture.completeGradleScript(
        """
        plugins {
            id("org.jetbrains.kotlin.jvm")
        }
        dependencies {
            <caret>
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).containsExactly(
          "annotationProcessor", "api", "compileOnly", "customSourceSetAnnotationProcessor", "customSourceSetApi",
          "customSourceSetCompileOnly", "customSourceSetImplementation", "customSourceSetImplementationDependenciesMetadata",
          "customSourceSetKotlinScriptDef", "customSourceSetKotlinScriptDefExtensions", "customSourceSetRuntimeOnly", "implementation",
          "implementationDependenciesMetadata", "kotlinBuildToolsApiClasspath", "kotlinCompilerClasspath", "kotlinCompilerPluginClasspath",
          "kotlinCompilerPluginClasspathCustomSourceSet", "kotlinCompilerPluginClasspathMain", "kotlinCompilerPluginClasspathTest",
          "kotlinInternalAbiValidation", "kotlinKlibCommonizerClasspath", "kotlinNativeCompilerPluginClasspath", "kotlinScriptDef",
          "kotlinScriptDefExtensions", "runtimeOnly", "testAnnotationProcessor", "testApi", "testCompileOnly", "testImplementation",
          "testImplementationDependenciesMetadata", "testKotlinScriptDef", "testKotlinScriptDefExtensions", "testRuntimeOnly",
          "implementation(\"myGroup1:myArtifact1:myVersion1\")", "implementation(\"myGroup2:myArtifact2:myVersion2\")",
          "implementation(\"myGroup3:myArtifact3:myVersion3\")", "implementation(\"fooGroup:compileArtifact:barVersion\")",
        )
        applySuggestion(1)
        checkResult(
          """
          plugins {
              id("org.jetbrains.kotlin.jvm")
          }
          dependencies {
              api(<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test configuration completion on top level partial input`() {
      codeInsightFixture.completeGradleScript(
        """
        plugins {
            id("org.jetbrains.kotlin.jvm")
        }
        dependencies {
            process<caret>
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).containsExactly(
          "annotationProcessor", "customSourceSetAnnotationProcessor", "testAnnotationProcessor",
          "implementation(\"myGroup1:myArtifact1:myVersion1\")", "implementation(\"myGroup2:myArtifact2:myVersion2\")",
          "implementation(\"myGroup3:myArtifact3:myVersion3\")", "implementation(\"fooGroup:compileArtifact:barVersion\")",
        )
        applySuggestion(2)
        checkResult(
          """
          plugins {
              id("org.jetbrains.kotlin.jvm")
          }
          dependencies {
              testAnnotationProcessor(<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test configuration without the accessor class is completed in quotes`() {
      codeInsightFixture.completeGradleScript(
        """
          val customSourceSet by sourceSets.registering {}
          dependencies {
              customSourceSet<caret>
          }
        """.trimIndent()
      ) {
        assertPreferredCompletionItems(0, "customSourceSetAnnotationProcessor", "customSourceSetApi")
        finishLookup(Lookup.REPLACE_SELECT_CHAR)
        checkResult(
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
    @ValueSource(booleans = [false, true])
    fun `test standard configuration is completed without quotes`(runInDumbMode: Boolean) =
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.completeGradleScript(
          """
            dependencies {
                impl<caret>
            }
          """.trimIndent()
        ) { lookupElements ->
          val implementationItem = lookupElements?.firstOrNull { it.lookupString == "implementation" }
          assertNotNull(implementationItem) { "Expected 'implementation' in the completion list" }
          lookup.currentItem = implementationItem
          finishLookup(Lookup.NORMAL_SELECT_CHAR)
          checkResult("""
            dependencies {
                implementation(<caret>)
            }
          """.trimIndent())
        }
      }

    @ParameterizedTest
    @CsvCrossProductSource("""
      dependencies { dependencie<caret> } : dependencies,
      dependencies { inn<caret> } : inn
    """)
    fun `test second completion invocation shows unfiltered input`(expression: String, expectedSuggestion: String) {
      LiveTemplateCompletionContributor.setShowTemplatesInTests(true, codeInsightFixture.testRootDisposable)
      codeInsightFixture.configureGradleScript(expression) {
        val invocationCount = 2
        complete(CompletionType.BASIC, invocationCount)
        assertThat(lookupElementStrings)
          .withFailMessage("Autocompletion was not expected (codeInsightFixture.lookupElementStrings returned null)")
          .isNotNull()
        assertThat(lookupElementStrings).contains(expectedSuggestion)
      }
    }

    @Test
    fun `test live templates are not shown if completion is not called at least twice`() {
      LiveTemplateCompletionContributor.setShowTemplatesInTests(true, codeInsightFixture.testRootDisposable)
      codeInsightFixture.assertCompletionDoesntSuggest(
        "dependencies { i<caret> }",
        unexpectedSuggestions = listOf("ifn", "inn", "interface", "iter")
      )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `test scope argument - suggest version catalogs and Dependency-returning methods`(runInDumbMode: Boolean) {
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.project.writeTextAndCommit(
          "gradle/libs.versions.toml", """
              [libraries]
              foo-bar-lib = { module = "org.junit.jupiter:junit-jupiter", version.ref = "6.0.0"  }
          """.trimIndent()
        )
        codeInsightFixture.completeGradleScript("dependencies { implementation(<caret>) }") {
          assertPreferredCompletionItems(
            0,
            // catalog names are always on top
            "customLibs",
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

    @Test
    fun `test scope argument - when quoted, don't suggest Dependency-returning methods`() {
      codeInsightFixture.assertCompletionDoesntSuggest(
        """dependencies { implementation("<caret>") }""",
        unexpectedSuggestions = listOf(
          "platform", "enforcedPlatform", "project", "kotlin", "embeddedKotlin", "testFixtures",
          "files", "fileTree", "variantOf", "gradleApi", "gradleTestKit"
        )
      )
    }

    @ParameterizedTest(name = "[{index}] {0}({1}) -> {0}({2})")
    @CsvCrossProductSource(
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
      configuration: String,
      input: String,
      expected: String,
    ) {
      codeInsightFixture.completeGradleScript("dependencies { $configuration($input) }") { lookupElements ->
        val isAutocompleted = lookupElements == null
        if (!isAutocompleted) {
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
        }
        checkResult("dependencies { $configuration($expected) }")
      }
    }

    @Test
    fun `test completion in a scope argument for an empty input`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [plugins]
          my-plugin-aaa = "my.plugin.aaa:1.0.0"
          [versions]
          my-version-aaa = "1.0.0"
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          [bundles]
          my-bundle-aaa = ["my-library-aaa"]
        """.trimIndent(),
        customLibs = """
          [libraries]
          my-library-bbb = "com.example:my-library-aaa:1.0.0"
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(<caret>)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith(
          "customLibs", "libs", "customLibs.my.library.bbb", "libs.bundles.my.bundle.aaa", "libs.my.library.aaa",
        )
      }
    }

    @Test
    fun `test catalog name completion in a scope argument`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
        """.trimIndent(),
        customLibs = """
          [libraries]
          my-library-bbb = "com.example:my-library-bbb:1.0.0"
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(lib<caret>)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("libs", "customLibs", "libs.my.library.aaa", "customLibs.my.library.bbb")
        applySuggestion(1)
        checkResult(
          """
          dependencies {
              implementation(customLibs<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test library entry completion in a scope argument`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          my-library-bbb = "com.example:my-library-bbb:1.0.0"
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(libs.m<caret>y.lib)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("my.library.aaa", "my.library.bbb")
        applySuggestion(0)
        checkResult(
          """
          dependencies {
              implementation(libs.my.library.aaa<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test library entry completion in a scope argument for a custom catalog`() {
      codeInsightFixture.writeVersionCatalogs(
        customLibs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          my-library-bbb = "com.example:my-library-bbb:1.0.0"
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(customLibs.m<caret>y.lib)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("my.library.aaa", "my.library.bbb")
        applySuggestion(1)
        checkResult(
          """
          dependencies {
              implementation(customLibs.my.library.bbb<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test bundle entry completion in a scope argument`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          my-library-bbb = "com.example:my-library-bbb:1.0.0"

          [bundles]
          my-bundle-aaa = ["my-library-aaa", "my-library-bbb"]
          my-bundle-bbb = ["my-library-bbb"]
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(libs.bundles.m<caret>y.bun)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("my.bundle.aaa", "my.bundle.bbb")
        applySuggestion(0)
        checkResult(
          """
          dependencies {
              implementation(libs.bundles.my.bundle.aaa<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test bundle entry completion when section is not specified`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          [bundles]
          my-bundle-aaa = ["my-library-aaa", "my-library-bbb"]
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(libs.m<caret>y.bun)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("my.library.aaa", "bundles.my.bundle.aaa")
        applySuggestion(1)
        checkResult(
          """
          dependencies {
              implementation(libs.bundles.my.bundle.aaa<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test libraries and bundles from multiple catalogs completion when a catalog name is not specified`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          [bundles]
          my-bundle-aaa = ["my-library-aaa"]
        """.trimIndent(),
        customLibs = """
          [libraries]
          my-library-bbb = "com.example:my-library-bbb:1.0.0"
          [bundles]
          my-bundle-bbb = ["my-library-bbb"]
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(my<caret>)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith(
          "customLibs.bundles.my.bundle.bbb", "customLibs.my.library.bbb", "libs.bundles.my.bundle.aaa", "libs.my.library.aaa",
        )
        applySuggestion(1)
        checkResult(
          """
          dependencies {
              implementation(customLibs.my.library.bbb<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test version catalog completion in an argument of a quoted scope`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [libraries]
          my-library-aaa = "com.example:my-library-aaa:1.0.0"
          my-library-bbb = "com.example:my-library-bbb:1.0.0"
          [bundles]
          my-bundle-aaa = ["my-library-aaa", "my-library-bbb"]
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        val customSourceSet by sourceSets.creating {}
        dependencies {
            "customSourceSetImplementation"(li<caret>)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("libs",
                                             "customLibs",
                                             "libs.bundles.my.bundle.aaa",
                                             "libs.my.library.aaa",
                                             "libs.my.library.bbb")
        applySuggestion(3)
        checkResult(
          """
          val customSourceSet by sourceSets.creating {}
          dependencies {
              "customSourceSetImplementation"(libs.my.library.aaa<caret>)
          }
          """.trimIndent()
        )
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation(platform(li<caret>)),
      implementation(enforcedPlatform(li<caret>)),
      testImplementation(testFixtures(li<caret>)),
      implementation(variantOf(li<caret>)),
      "customSourceSetImplementation"(variantOf(li<caret>))
    """, "false,true")
    fun `test version catalog completion in allowed DependencyHandler methods`(expression: String, runInDumbMode: Boolean) {
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.project.writeTextAndCommit(
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
        codeInsightFixture.completeGradleScript("dependencies { $expression }") {
          assertPreferredCompletionItems(
            0,
            "libs",
            "customLibs",
            "libs.bundles.my.bundle.aaa",
            "libs.my.library.aaa"
          )
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
          checkResult("dependencies { ${expression.replace("li<caret>", "libs")} }")
        }
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation(project(li<caret>)),
      implementation(files(li<caret>))
    """, "false,true")
    fun `test version catalogs are not suggested in inapplicable methods`(expression: String, runInDumbMode: Boolean) {
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.project.writeTextAndCommit(
          "gradle/libs.versions.toml",
          """
            [libraries]
            my-library-aaa = "com.example:my-library-aaa:1.0.0"
            [bundles]
            my-bundle-aaa = ["my-library-aaa"]
          """.trimIndent()
        )
        codeInsightFixture.assertCompletionDoesntSuggest(
          "dependencies { $expression }",
          listOf("libs", "libs.bundles.my.bundle.aaa", "libs.my.library.aaa")
        )
      }
    }

    @Test
    fun `test plugins and versions are not completed in when the section is not specified`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [versions]
          my-version = "1.0.0"

          [plugins]
          my-plugin = "my.plugin:1.0.0"
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(libs.my<caret>)
        }
        """.trimIndent()
      ) { lookupElements ->
        assertThat(lookupElements).isNullOrEmpty()
      }
    }

    @Test
    fun `test versions are completed in when the section is specified`() {
      codeInsightFixture.writeVersionCatalogs(
        libs = """
          [versions]
          my-version-aaa = "1.0.0"
          my-version-bbb = "1.0.0"
        """.trimIndent(),
      )
      codeInsightFixture.completeGradleScript(
        """
        dependencies {
            implementation(libs.versions.my<caret>)
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).startsWith("my.version.aaa", "my.version.bbb")
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS, "false,true")
    fun `test coordinates completion in dependency configuration`(dependencyConfigurationEscaped: String, runInDumbMode: Boolean) {
      val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        val parts = dependencyConfiguration.split(":")

        val dependencyEntryTemplate = if (parts.size > 1)
          "${parts[0]}(${parts[1]}(\"<>\"))"
        else
          "$dependencyConfiguration(\"<>\")"

        val dependencyEntry = dependencyEntryTemplate.replace("<>", "myGrou<caret>")
        val dependencyCompletionResult = dependencyEntryTemplate.replace("<>", "myGroup1:myArtifact1:myVersion1")

        codeInsightFixture.completeGradleScript("dependencies { $dependencyEntry }") {
          assertPreferredCompletionItems(
            0, "myGroup1:myArtifact1:myVersion1",
            "myGroup2:myArtifact2:myVersion2",
            "myGroup3:myArtifact3:myVersion3",
          )
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
          checkResult("dependencies { $dependencyCompletionResult }")
        }
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation("<caret>myGroup1<colon>myArtifact1<colon>myVersion1"),
      implementation("myGr<caret>something"),
      implementation("myGroup1<colon>myArti<caret><colon><colon>"),
      implementation("myGroup1<colon>myArtifact1<colon>myVer<caret>si")
    """)
    fun `test coordinates completion when caret is in the middle of the dependency string`(dependencyEntryEscaped: String) {
      val dependencyEntry = dependencyEntryEscaped.unescape()
      val dependencyCompletionResult = "implementation(\"myGroup1:myArtifact1:myVersion1\")"

      codeInsightFixture.completeGradleScript("dependencies { $dependencyEntry }") {
        assertPreferredCompletionItems(
          0, "myGroup1:myArtifact1:myVersion1",
          "myGroup2:myArtifact2:myVersion2",
          "myGroup3:myArtifact3:myVersion3",
        )
        finishLookup(Lookup.NORMAL_SELECT_CHAR)
        checkResult("dependencies { $dependencyCompletionResult }")
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource(DEPENDENCY_CONFIGURATIONS_AND_NOTATIONS, "false,true")
    fun `test coordinates completion configuration names`(dependencyConfigurationEscaped: String, runInDumbMode: Boolean) {
      val dependencyConfiguration = dependencyConfigurationEscaped.unescape()
      val substitutionResult = if (dependencyConfiguration.contains(",")) {
        val items = dependencyConfiguration.split(",")
        "${items[0]}(${items[1]}(\"g:a:v\"))"
      }
      else "$dependencyConfiguration(\"g:a:v\")"

      codeInsightFixture.replaceCompletionService(object : DependencyCompletionService {
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
      })
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.completeGradleScript("dependencies { a<caret> }") { lookupElements ->
          lookup.currentItem = lookupElements!!.find { it.lookupString.contains(substitutionResult) }
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
          checkResult("dependencies { $substitutionResult }")
        }
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
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
    fun `test coordinates completion in dependencies`(completionEscaped: String) {
      val completion = completionEscaped.unescape()
      codeInsightFixture.replaceCompletionService(object : DependencyCompletionService {
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
      })
      codeInsightFixture.completeGradleScript("dependencies { $completion }") { lookupElements ->
        lookup.currentItem = lookupElements!!.find {
          it.lookupString.contains("implementation(\"org.example.p:my-long-artifact-id:2.7.0\")")
        }
        finishLookup(Lookup.REPLACE_SELECT_CHAR)
        checkResult("dependencies { implementation(\"org.example.p:my-long-artifact-id:2.7.0\") }")
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
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
    fun `test group completion in dependencies`(completionEscaped: String) {
      val completion = completionEscaped.unescape()
      val completionResult = completion.replace("<caret>", "g")
      codeInsightFixture.replaceCompletionService(object : DependencyCompletionService {
        override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
          return flowOf(
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("g", source = LOCAL)),
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("h", source = LOCAL))
          )
        }
      })
      codeInsightFixture.completeGradleScript("dependencies { $completion }") { lookupElements ->
        lookup.currentItem = lookupElements!!.find { it.lookupString.contains("g") }
        finishLookup(Lookup.REPLACE_SELECT_CHAR)
        checkResult("dependencies { $completionResult }")
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
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
    fun `test artifact completion in dependencies`(completionEscaped: String) {
      val completion = completionEscaped.unescape()
      val completionResult = completion.replace("<caret>", "a")
      codeInsightFixture.replaceCompletionService(object : DependencyCompletionService {
        override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
          return flowOf(
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("a", source = LOCAL)),
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("b", source = LOCAL))
          )
        }
      })
      codeInsightFixture.completeGradleScript("dependencies { $completion }") { lookupElements ->
        lookup.currentItem = lookupElements!!.find { it.lookupString.contains("a") }
        finishLookup(Lookup.REPLACE_SELECT_CHAR)
        checkResult("dependencies { $completionResult }")
      }
    }

    @Test
    fun testArtifactCompletionOnTopLevel() {
      codeInsightFixture.completeGradleScript(
        """
        plugins {
            id("java")
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            myGrou<caret>
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).containsExactly(
          "implementation(\"myGroup1:myArtifact1:myVersion1\")", "implementation(\"myGroup2:myArtifact2:myVersion2\")",
          "implementation(\"myGroup3:myArtifact3:myVersion3\")", "implementation(\"fooGroup:compileArtifact:barVersion\")",
        )
        applySuggestion(1)
        checkResult(
          """
          plugins {
              id("java")
          }

          repositories {
              mavenCentral()
          }

          dependencies {
              implementation("myGroup2:myArtifact2:myVersion2")
          }
          """.trimIndent()
        )
      }
    }

    @Test
    fun `test K2 Contributor is not ignored in unsupported cases`() {
      codeInsightFixture.completeGradleScript(
        """
          dependencies {
            implementation("org.junit.jupiter:junit-jupiter:6.0.0") {
              ex<caret>
            }
          }
        """.trimIndent()
      ) { lookupElements ->
        val expectedElement = lookupElements?.find {
          it.contributorClass?.name == "org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2CallableCompletionContributor"
          && it.lookupString == "exclude"
        }
        assertNotNull(expectedElement, "Expected to find an element produced by K2 Completion Contributor, but it wasn't found")
      }
    }

    @Test
    fun `test command completion is not shown in the dependencies block`() {
      Registry.get("ide.completion.command.force.enabled").setValue(true, codeInsightFixture.testRootDisposable)
      codeInsightFixture.completeGradleScript(
        """
          dependencies {
              implementation("org.junit.jupiter:junit-jupiter:6.0.0").<caret>
          }
        """.trimIndent()
      ) { lookupElements ->
        val lookupStrings = lookupElements?.map { it.lookupString }.orEmpty()
        assertThat(lookupStrings)
          .doesNotContain("Surround with 'try / finally'", "Extract function", "Go to members", "Reformat code")
      }
    }

    @Test
    fun `test command completion is not disabled outside the dependencies block`() {
      Registry.get("ide.completion.command.force.enabled").setValue(true, codeInsightFixture.testRootDisposable)
      codeInsightFixture.completeGradleScript("{}.refor<caret>") { lookupElements ->
        val lookupStrings = lookupElements?.map { it.lookupString }.orEmpty()
        assertThat(lookupStrings).contains("Reformat code")
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation(kotlin("std<caret>")),
      implementation(kotlin("<caret>")),
      implementation(kotlin(module="std<caret>")),
      implementation(kotlin(module="<caret>"))
    """, "false,true")
    fun `test kotlin shortcut module completion`(completionEscaped: String, runInDumbMode: Boolean) {
      val completion = completionEscaped.unescape()
      val completionResult = completion.replace(Regex("\"[^\"]*<caret>\""), "\"stdlib:2.0.21\"")
      codeInsightFixture.replaceCompletionService(object : DependencyCompletionService {
        override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> {
          return flowOf(
            DependencyCompletionEvent.Item(DependencyCompletionResult("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21", source = LOCAL)),
            DependencyCompletionEvent.Item(DependencyCompletionResult("org.jetbrains.kotlin", "kotlin-reflect", "2.0.21", source = LOCAL)),
            DependencyCompletionEvent.Item(DependencyCompletionResult("org.example", "unrelated", "1.0", source = LOCAL)),
          )
        }
      })
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.completeGradleScript("dependencies { $completion }") {
          assertPreferredCompletionItems(0, "stdlib:2.0.21", "reflect:2.0.21")
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
          checkResult("dependencies { $completionResult }")
        }
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation(embeddedKotlin("std<caret>")),
      implementation(embeddedKotlin("<caret>")),
      implementation(embeddedKotlin(module="std<caret>")),
      implementation(embeddedKotlin(module="<caret>"))
    """, "false,true")
    fun `test embeddedKotlin shortcut module completion`(completionEscaped: String, runInDumbMode: Boolean) {
      val completion = completionEscaped.unescape()
      // `embeddedKotlin` accepts no version argument, so completion must produce only the module name.
      val completionResult = completion.replace(Regex("\"[^\"]*<caret>\""), "\"stdlib\"")
      codeInsightFixture.replaceCompletionService(object : DependencyCompletionService {
        override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
          return flowOf(
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("kotlin-stdlib", source = LOCAL)),
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("kotlin-reflect", source = LOCAL)),
            DependencyCompletionEvent.Item(DependencyPartCompletionResult("unrelated", source = LOCAL)),
          )
        }
      })
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.completeGradleScript("dependencies { $completion }") {
          assertPreferredCompletionItems(0, "stdlib", "reflect")
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
          checkResult("dependencies { $completionResult }")
        }
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation(kotlin("stdlib"<comma> "1.0<caret>")),
      implementation(kotlin("stdlib"<comma> "<caret>")),
      implementation(kotlin(module="stdlib"<comma> version="<caret>")),
      implementation(kotlin(version="<caret>"<comma> module="stdlib"))
    """, "false,true")
    fun `test kotlin shortcut version completion`(completionEscaped: String, runInDumbMode: Boolean) {
      val completion = completionEscaped.unescape()
      val completionResult = completion.replace(Regex("\"[^\"]*<caret>\""), "\"2.0.21\"")
      codeInsightFixture.replaceCompletionService(versionCompletionService)
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.completeGradleScript("dependencies { $completion }") {
          assertPreferredCompletionItems(0, "2.0.21", "1.9.24")
          finishLookup(Lookup.REPLACE_SELECT_CHAR)
          checkResult("dependencies { $completionResult }")
        }
      }
    }

    @ParameterizedTest
    @CsvCrossProductSource("""
      implementation(embeddedKotlin("stdlib"<comma> "1.0<caret>")),
      implementation(embeddedKotlin("stdlib"<comma> "<caret>")),
      implementation(embeddedKotlin(module="stdlib"<comma> version="<caret>")),
      implementation(embeddedKotlin(version="<caret>"<comma> module="stdlib"))
    """, "false,true")
    fun `test embeddedKotlin does not offer version completion`(completionEscaped: String, runInDumbMode: Boolean) {
      val completion = completionEscaped.unescape()
      codeInsightFixture.replaceCompletionService(versionCompletionService)
      codeInsightFixture.runInDumbModeIf(runInDumbMode) {
        codeInsightFixture.completeGradleScript("dependencies { $completion }") {
          // `embeddedKotlin` accepts no version argument, so no version suggestions should be produced.
          assertThat(lookupElementStrings.orEmpty()).doesNotContain("2.0.21", "1.9.24")
        }
      }
    }

    @Test
    fun `test configuration names are not suggested for coordinate-like input`() {
      // "junit-api" contains a coordinate separator, so configuration names that merely contain "api"
      // (api, testApi, ...) must not be suggested; only dependency coordinates should appear.
      codeInsightFixture.assertCompletionDoesntSuggest(
        "dependencies { junit-api<caret> }",
        listOf("api", "testApi", "apiDependenciesMetadata", "testApiDependenciesMetadata")
      )
    }
  }

  @Nested
  @ParameterizedClass
  @GradleTestSource("8.0")
  inner class Gradle80(gradleVersion: GradleVersion) {

    private val codeInsightFixture by versionCatalogsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun `replace completion service`() {
      codeInsightFixture.replaceCompletionService(testCompletionService)
    }

    @Test
    fun `test completion before 8,2 also shows configurations that couldn't be used in dependencies block`() {
      codeInsightFixture.completeGradleScript(
        """
        plugins {
            id("org.jetbrains.kotlin.jvm")
        }
        dependencies {
            // `archives` cannot declare dependencies
            arc<caret>
        }
        """.trimIndent()
      ) {
        assertThat(suggestions()).containsExactly(
          "archives", "implementation(\"myGroup1:myArtifact1:myVersion1\")", "implementation(\"myGroup2:myArtifact2:myVersion2\")",
          "implementation(\"myGroup3:myArtifact3:myVersion3\")", "implementation(\"fooGroup:compileArtifact:barVersion\")",
        )
        applySuggestion(0)
        checkResult(
          """
          plugins {
              id("org.jetbrains.kotlin.jvm")
          }
          dependencies {
              // `archives` cannot declare dependencies
              archives(<caret>)
          }
          """.trimIndent()
        )
      }
    }
  }

  private val versionCompletionService = object : DependencyCompletionService {
    override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> {
      return flowOf(
        DependencyCompletionEvent.Item(DependencyPartCompletionResult("2.0.21", source = LOCAL)),
        DependencyCompletionEvent.Item(DependencyPartCompletionResult("1.9.24", source = LOCAL)),
      )
    }
  }

  private fun JavaCodeInsightTestFixture.replaceCompletionService(service: DependencyCompletionService) {
    application.replaceService(DependencyCompletionService::class.java, service, testRootDisposable)
  }

  private fun JavaCodeInsightTestFixture.assertCompletionDoesntSuggest(text: String, unexpectedSuggestions: List<String>) {
    configureGradleScript(text) {
      val contentBeforeCompletion = file.text

      completeBasic()
      val suggestions = lookupElementStrings
      if (suggestions == null) {
        // check that nothing was autocompleted
        checkResult(contentBeforeCompletion)
      }
      else {
        assertThat(suggestions).doesNotContainAnyElementsOf(unexpectedSuggestions)
      }
    }
  }
}
