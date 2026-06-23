// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.common.runAll
import com.intellij.util.io.DigestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ValueSource(strings = [COMMON_MAIN, ANDROID_MAIN, IOS_MAIN])
annotation class ComposeResourcesAllSourceSets

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ValueSource(strings = [COMMON_MAIN])
annotation class ComposeResourcesCommonMainOnly

@ParameterizedClass(name = "source set {0}")
@GradleProjectTestApplication
abstract class ComposeResourcesCodeInsightTestCase : GradleCodeInsightBaseTestCase() {
  @Parameter
  protected lateinit var sourceSetName: String

  override fun tearDown() {
    runAll(
      { KotlinSdkType.removeKotlinSdkInTests() },
      { super.tearDown() }
    )
  }

  protected fun testComposeResourcesProject(test: () -> Unit) {
    test(GradleVersion.version(TARGET_GRADLE_VERSION), COMPOSE_RESOURCES_PROJECT, test)
  }

  /**
   * `VirtualFile.getCanonicalFile` is not enough here: it resolves through the VFS without refreshing, so it returns
   * `null` while the real path (`/var` vs `/private/var` on macOS) is not in the VFS yet.
   */
  protected fun canonicalProjectFile(relativePath: String) =
    getFile(relativePath).let { file ->
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.toNioPath().toRealPath()) ?: file
    }

  protected fun snapshotProjectFile(relativePath: String) {
    gradleFixture.fileFixture.snapshot(relativePath)
  }

  companion object {
    private val COMPOSE_RESOURCES_PROJECT = GradleTestFixtureBuilder.create("ComposeResources") {
      excludeFilePatterns(
        "glob:**/composeApp/src/commonMain/root.png",
        "glob:**/composeApp/src/commonMain/test.png",
        "glob:**/composeApp/src/commonMain/composeResources/drawable/*.png",
      )
      withFile(".gradle/testDataFingerprint", composeResourcesTestDataFingerprint())
      withFiles { projectRoot ->
        val testDataRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(
          composeResourcesTestDataRoot().toString()
        ) ?: error("Cannot find ComposeResources test data")
        edtWriteAction {
          VfsUtil.copyDirectory(this, testDataRoot, projectRoot) { file ->
            val relativePath = requireNotNull(VfsUtil.getRelativePath(file, testDataRoot)) {
              "$file is not under $testDataRoot"
            }
            isStableTestDataPath(relativePath.split('/'))
          }
        }
        AndroidStudioTestUtils.specifyAndroidSdk(projectRoot.toNioPath())
      }
    }

    private fun composeResourcesTestDataRoot(): Path =
      Path.of(PathManagerEx.getCommunityHomePath(), "plugins/compose/intellij.compose.ide.plugin.resources/testData/ComposeResources")

    private fun composeResourcesTestDataFingerprint(): String {
      val root = composeResourcesTestDataRoot()
      val digest = DigestUtil.sha256()
      Files.walk(root).use { paths ->
        paths
          .filter(Files::isRegularFile)
          .filter { path -> isStableTestDataPath(relativeNames(root, path)) }
          .sorted()
          .forEach { path ->
            digest.update(root.relativize(path).invariantSeparatorsPathString.toByteArray())
            digest.update(0)
            DigestUtil.updateContentHash(digest, path)
          }
      }
      return DigestUtil.digestToHash(digest)
    }

    private fun relativeNames(root: Path, path: Path): List<String> =
      root.relativize(path).map { it.toString() }

    private fun isStableTestDataPath(relativeNames: List<String>): Boolean =
      relativeNames.lastOrNull() != "local.properties" &&
      relativeNames.none { it == "build" || it in GENERATED_TEST_DATA_ROOT_NAMES }

    private val GENERATED_TEST_DATA_ROOT_NAMES = setOf(".gradle", ".idea", ".kotlin")
  }
}
