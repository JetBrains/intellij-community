// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.devkit.gradle.toml.TomlIntelliJPlatformVersionCatalogUpdater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class IntelliJPlatformGradlePluginVersionQuickFixTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    project.registerOrReplaceServiceInstance(
      IntelliJPlatformVersionCatalogUpdater::class.java,
      TomlIntelliJPlatformVersionCatalogUpdater(),
      testRootDisposable,
    )
    project.registerOrReplaceServiceInstance(
      IntelliJPlatformGradleModelProvider::class.java,
      IntelliJPlatformGradleModelProvider {
        IntelliJPlatformGradleData(currentPluginVersion = CURRENT_VERSION, latestPluginVersion = LATEST_VERSION)
      },
      testRootDisposable,
    )
    ExtensionTestUtil.maskExtensions(
      ProblemHighlightFilter.EP_NAME,
      listOf(object : ProblemHighlightFilter() {
        override fun shouldHighlight(file: PsiFile): Boolean = true

        override fun shouldProcessInBatch(file: PsiFile): Boolean = true
      }),
      testRootDisposable,
    )
  }

  fun testUpdatesPluginVersion() {
    for ((fileName, before, after) in versionDeclarations()) {
      val file = myFixture.configureByText(fileName, before)
      WriteCommandAction.runWriteCommandAction(project) {
        assertEquals(
          "$fileName: $before",
          before != after,
          updateIntelliJPlatformGradlePluginVersion(file, CURRENT_VERSION, LATEST_VERSION),
        )
      }
      myFixture.checkResult(after)
    }
  }

  fun testOffersQuickFixOnSettingsPluginVersion() {
    assertQuickFix(
      "settings.gradle.kts",
      """plugins { id("org.jetbrains.intellij.platform.settings") version "2.1.0" }""",
      """plugins { id("org.jetbrains.intellij.platform.settings") version "2.2.1" }""",
    )
  }

  fun testOffersQuickFixOnVersionCatalogPluginVersion() {
    assertQuickFix(
      "libs.versions.toml",
      """[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version = "2.1.0" }""",
      """[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version = "2.2.1" }""",
    )
  }

  fun testOffersQuickFixOnPreReleasePluginVersion() {
    project.registerOrReplaceServiceInstance(
      IntelliJPlatformGradleModelProvider::class.java,
      IntelliJPlatformGradleModelProvider {
        IntelliJPlatformGradleData(currentPluginVersion = "2.0.0-beta9", latestPluginVersion = LATEST_VERSION)
      },
      testRootDisposable,
    )
    assertQuickFix(
      "libs.versions.toml",
      """[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version = "2.0.0-beta9" }""",
      """[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version = "2.2.1" }""",
      expectedOldVersion = "2.0.0-beta9",
    )
  }

  private fun assertQuickFix(
    fileName: String,
    before: String,
    after: String,
    expectedOldVersion: String = CURRENT_VERSION,
  ) {
    val inspection = OutdatedIntelliJPlatformGradlePluginVersionInspection()
    myFixture.enableInspections(inspection)
    val file = myFixture.configureByText(fileName, before)
    assertTrue(inspection.isAvailableForFile(file))
    val problem = assertOneElement(myFixture.doHighlighting().filter { it.inspectionToolId == inspection.id })

    assertEquals("\"$expectedOldVersion\"", file.text.substring(problem.startOffset, problem.endOffset))
    myFixture.editor.caretModel.moveToOffset(problem.startOffset + 1)
    myFixture.launchAction(myFixture.findSingleIntention("Update to 2.2.1"))
    myFixture.checkResult(after)
  }

  companion object {
    private const val CURRENT_VERSION = "2.1.0"
    private const val LATEST_VERSION = "2.2.1"

    private fun versionDeclarations(): List<TestData> = listOf(
      TestData(
        "build.gradle.kts",
        """plugins { id("org.jetbrains.intellij.platform") version "2.1.0" }""",
        """plugins { id("org.jetbrains.intellij.platform") version "2.2.1" }""",
      ),
      TestData(
        "settings.gradle.kts",
        """pluginManagement { plugins { id("org.jetbrains.intellij.platform") version("2.1.0") } }""",
        """pluginManagement { plugins { id("org.jetbrains.intellij.platform") version("2.2.1") } }""",
      ),
      TestData(
        "settings.gradle.kts",
        """plugins { id("org.jetbrains.intellij.platform.settings") version "2.1.0" }""",
        """plugins { id("org.jetbrains.intellij.platform.settings") version "2.2.1" }""",
      ),
      TestData(
        "settings.gradle.kts",
        """plugins { id("org.jetbrains.intellij.platform.base") version "2.1.0" }""",
        """plugins { id("org.jetbrains.intellij.platform.base") version "2.2.1" }""",
      ),
      TestData(
        "settings.gradle.kts",
        """plugins {
  id("org.jetbrains.intellij.platform.settings") version "2.1.0"
  id("org.jetbrains.intellij.platform.base") version "2.1.0"
}""",
        """plugins {
  id("org.jetbrains.intellij.platform.settings") version "2.2.1"
  id("org.jetbrains.intellij.platform.base") version "2.2.1"
}""",
      ),
      TestData(
        "build.gradle.kts",
        """classpath("org.jetbrains.intellij.platform:intellij-platform-gradle-plugin:2.1.0")""",
        """classpath("org.jetbrains.intellij.platform:intellij-platform-gradle-plugin:2.2.1")""",
      ),
      TestData(
        "libs.versions.toml",
        """[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform.base", version = "2.1.0" }""",
        """[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform.base", version = "2.2.1" }""",
      ),
      TestData(
        "custom.versions.toml",
        """[plugins]
intellij-platform = "org.jetbrains.intellij.platform:2.1.0"""",
        """[plugins]
intellij-platform = "org.jetbrains.intellij.platform:2.2.1"""",
      ),
      TestData(
        "libs.versions.toml",
        """[versions]
intellij-platform = "2.1.0"
other = "2.1.0"

[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version.ref = "intellij-platform" }""",
        """[versions]
intellij-platform = "2.2.1"
other = "2.1.0"

[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version.ref = "intellij-platform" }""",
      ),
      TestData(
        "libs.versions.toml",
        """[versions]
intellij-platform = "2.1.0"
other = "2.1.0"

[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version = { ref = "intellij-platform" } }""",
        """[versions]
intellij-platform = "2.2.1"
other = "2.1.0"

[plugins]
intellij-platform = { id = "org.jetbrains.intellij.platform", version = { ref = "intellij-platform" } }""",
      ),
      TestData(
        "plugin.gradle.kts",
        """plugins { id("org.jetbrains.intellij.platform") apply false version "2.1.0" }""",
        """plugins { id("org.jetbrains.intellij.platform") apply false version "2.2.1" }""",
      ),
      TestData(
        "build.gradle.kts",
        """plugins {
  id("org.jetbrains.intellij.platform") version "2.0.0"
}
dependencies { implementation("example:library:2.1.0") }""",
        """plugins {
  id("org.jetbrains.intellij.platform") version "2.0.0"
}
dependencies { implementation("example:library:2.1.0") }""",
      ),
      TestData(
        "build.gradle.kts",
        """plugins { id("org.jetbrains.intellij.platformish") version "2.1.0" }""",
        """plugins { id("org.jetbrains.intellij.platformish") version "2.1.0" }""",
      ),
      TestData(
        "build.gradle.kts",
        """plugins { foo("org.jetbrains.intellij.platform") version "2.1.0" }""",
        """plugins { foo("org.jetbrains.intellij.platform") version "2.1.0" }""",
      ),
    )
  }

  private data class TestData(val fileName: String, val before: String, val after: String)
}
