// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import com.intellij.util.application

internal class IntelliJPlatformVersionsCompletionContributorTest : LightJavaCodeInsightFixtureTestCase() {

  private var gradleData = IntelliJPlatformGradleData(
    dependencyHelperProductCodes = mapOf(
      "intellijIdea" to "IU",
      "intellijIdeaCommunity" to "IC",
    ),
    productReleases = mapOf(
      "IC" to listOf(release("2023.1"), release("2023.2")),
      "IU" to listOf(release("2023.2"), release("2024.1")),
    ),
    bundledPlugins = listOf(
      IntelliJPlatformBundledArtifact("com.intellij.java", "Java"),
      IntelliJPlatformBundledArtifact("org.jetbrains.kotlin", "Kotlin"),
    ),
    bundledModules = listOf(
      IntelliJPlatformBundledArtifact("intellij.platform.vcs.impl", "VCS Implementation"),
      IntelliJPlatformBundledArtifact("intellij.java.psi", "Java PSI"),
    ),
  )
  private var modelRequestFile: PsiFile? = null

  override fun setUp() {
    super.setUp()
    project.replaceService(
      IntelliJPlatformGradleModelProvider::class.java,
      IntelliJPlatformGradleModelProvider { file ->
        modelRequestFile = file
        gradleData
      },
      testRootDisposable,
    )
    val contributor = CompletionContributorEP(
      "kotlin",
      IntelliJPlatformVersionsCompletionContributor::class.java.name,
      DefaultPluginDescriptor("testIntelliJPlatformVersionsCompletion"),
    )
    application.registerExtension(CompletionContributor.EP, contributor, testRootDisposable)
  }

  fun testSuggestsPlatformVersionInKotlinStringLiteral() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            intellijIdea("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertPlatformVersions("2023.2", "2024.1")
    assertSame(myFixture.file, modelRequestFile)
  }

  fun testSuggestsOnlyVersionsForDependencyHelperProductType() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            intellijIdeaCommunity("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertPlatformVersions("2023.1", "2023.2")
  }

  fun testRendersProductDetailsAndPrioritizesDottedVersionsNewestFirst() {
    val versions = listOf("2025.2.6", "2025.2.6.3", "2025.2.6.2", "2025.2.6.1")
    gradleData = gradleData.copy(
      productReleases = mapOf(
        "IU" to versions.map { version ->
          release(version, channel = if (version == "2025.2.6.2") "EAP" else "RELEASE")
        },
      ),
    )
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            intellijIdea("<caret>")
          }
        }
      """.trimIndent(),
    )

    val elements = completeBasicElements()
    assertEquals(
      mapOf(
        "2025.2.6.3" to 104.0,
        "2025.2.6.2" to 103.0,
        "2025.2.6.1" to 102.0,
        "2025.2.6" to 101.0,
      ),
      elements.filter { it.lookupString in versions }.associate {
        it.lookupString to it.`as`(PrioritizedLookupElement.CLASS_CONDITION_KEY)?.priority
      },
    )

    val presentation = LookupElementPresentation()
    elements.first { it.lookupString == "2025.2.6.3" }.renderElement(presentation)
    assertSame(DevkitCoreIcons.Sdk_closed, presentation.icon)
    assertEquals(" Release", presentation.tailText)
    assertEquals("IntelliJ IDEA (IU)", presentation.typeText)

    val eapPresentation = LookupElementPresentation()
    elements.first { it.lookupString == "2025.2.6.2" }.renderElement(eapPresentation)
    assertEquals(" EAP", eapPresentation.tailText)
  }

  fun testSuggestsPlatformVersionForCreateVersionArgument() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            create("IU", "<caret>")
          }
        }
      """.trimIndent(),
    )

    assertPlatformVersions("2023.2", "2024.1")
  }

  fun testSuggestsPlatformVersionForCreateNamedTypeArgument() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            create(version = "<caret>", type = "IC")
          }
        }
      """.trimIndent(),
    )

    assertPlatformVersions("2023.1", "2023.2")
  }

  fun testDoesNotSuggestPlatformVersionForCreateTypeArgument() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            create("<caret>", "2026.1")
          }
        }
      """.trimIndent(),
    )

    assertNoPlatformVersions()
  }

  fun testDoesNotSuggestPlatformVersionForUnsupportedDependencyHelper() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            plugin("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertNoPlatformVersions()
  }

  fun testDoesNotSuggestPlatformVersionWithoutImportedProductReleases() {
    gradleData = gradleData.copy(productReleases = emptyMap())
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            intellijIdea("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertNoPlatformVersions()
  }

  fun testDoesNotSuggestPlatformVersionOutsideKotlinStringLiteral() {
    myFixture.configureByText("build.gradle.kts", "val <caret>")

    assertNoPlatformVersions()
  }

  fun testDoesNotSuggestPlatformVersionOutsideGradleKotlinScript() {
    myFixture.configureByText(
      "Example.kt",
      """
        dependencies {
          intellijPlatform {
            intellijIdea("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertNoPlatformVersions()
  }

  fun testSuggestsBundledPluginInKotlinStringLiteral() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledPlugin("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
    val elements = completeBasicElements()
    val presentation = LookupElementPresentation()
    elements.first { it.lookupString == "com.intellij.java" }.renderElement(presentation)
    assertSame(AllIcons.Nodes.Plugin, presentation.icon)
    assertEquals(" (Java)", presentation.tailText)
    assertEquals("Bundled Plugin", presentation.typeText)
  }

  fun testSuggestsBundledPluginsInVarargs() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledPlugins("com.intellij.java", "<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
  }

  fun testSuggestsTestBundledPlugin() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            testBundledPlugin("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
  }

  fun testSuggestsTestBundledPlugins() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            testBundledPlugins("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
  }

  fun testSuggestsBundledModuleInKotlinStringLiteral() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledModule("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledModules("intellij.platform.vcs.impl", "intellij.java.psi")
    val elements = completeBasicElements()
    val presentation = LookupElementPresentation()
    elements.first { it.lookupString == "intellij.platform.vcs.impl" }.renderElement(presentation)
    assertSame(DevkitCoreIcons.PluginModule, presentation.icon)
    assertEquals(" (VCS Implementation)", presentation.tailText)
    assertEquals("Bundled Module", presentation.typeText)
  }

  fun testSuggestsBundledModulesInVarargs() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledModules("intellij.platform.vcs.impl", "<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledModules("intellij.platform.vcs.impl", "intellij.java.psi")
  }

  fun testSuggestsTestBundledModule() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            testBundledModule("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledModules("intellij.platform.vcs.impl", "intellij.java.psi")
  }

  fun testSuggestsTestBundledModules() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            testBundledModules("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledModules("intellij.platform.vcs.impl", "intellij.java.psi")
  }

  fun testDoesNotSuggestBundledPluginsWithoutData() {
    gradleData = gradleData.copy(bundledPlugins = emptyList())
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledPlugin("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertNoBundledPlugins()
  }

  fun testDoesNotSuggestBundledModulesWithoutData() {
    gradleData = gradleData.copy(bundledModules = emptyList())
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledModule("<caret>")
          }
        }
      """.trimIndent(),
    )

    assertNoBundledModules()
  }

  fun testSuggestsBundledPluginsCaseInsensitively() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledPlugin("COM.INTELLIJ<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledPlugins("com.intellij.java")
  }

  fun testSuggestsBundledModulesCaseInsensitively() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            bundledModule("INTELLIJ.PLATFORM<caret>")
          }
        }
      """.trimIndent(),
    )

    assertBundledModules("intellij.platform.vcs.impl")
  }

  fun testSuggestsCreateHelperProductReleasesCaseInsensitively() {
    myFixture.configureByText(
      "build.gradle.kts",
      """
        dependencies {
          intellijPlatform {
            create(type = "iu", version = "<caret>")
          }
        }
      """.trimIndent(),
    )

    assertPlatformVersions("2023.2", "2024.1")
  }

  private fun completeBasic(): List<String> {
    return completeBasicElements().map { it.lookupString }
  }

  private fun completeBasicElements(): List<LookupElement> {
    val settings = CodeInsightSettings.getInstance()
    val autoComplete = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
    return try {
      myFixture.completeBasic().orEmpty().toList()
    }
    finally {
      settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
    }
  }

  private fun assertNoPlatformVersions() {
    assertDoesntContain(completeBasic(), *PLATFORM_VERSIONS.toTypedArray())
  }

  private fun assertPlatformVersions(vararg expected: String) {
    assertEquals(expected.toSet(), completeBasic().filter { it in PLATFORM_VERSIONS }.toSet())
  }

  private fun assertNoBundledPlugins() {
    assertDoesntContain(completeBasic(), *BUNDLED_PLUGINS.toTypedArray())
  }

  private fun assertBundledPlugins(vararg expected: String) {
    assertEquals(expected.toSet(), completeBasic().filter { it in BUNDLED_PLUGINS }.toSet())
  }

  private fun assertNoBundledModules() {
    assertDoesntContain(completeBasic(), *BUNDLED_MODULES.toTypedArray())
  }

  private fun assertBundledModules(vararg expected: String) {
    assertEquals(expected.toSet(), completeBasic().filter { it in BUNDLED_MODULES }.toSet())
  }

  companion object {
    private val PLATFORM_VERSIONS = listOf("2023.1", "2023.2", "2024.1")
    private val BUNDLED_PLUGINS = listOf("com.intellij.java", "org.jetbrains.kotlin")
    private val BUNDLED_MODULES = listOf("intellij.platform.vcs.impl", "intellij.java.psi")

    private fun release(version: String, channel: String = "RELEASE") = IntelliJPlatformProductRelease(version, channel)
  }
}
