// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.util.io.DigestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

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
      { removeAndroidSdks() },
      { super.tearDown() }
    )
  }

  /**
   * The Gradle sync creates an Android SDK from the local Android SDK path, and the SDK leak tracker reports it.
   * The name prefix is `com.android.tools.idea.sdk.AndroidSdks.SDK_NAME_PREFIX`, which this module cannot depend on.
   */
  private fun removeAndroidSdks() {
    invokeAndWaitIfNeeded {
      runWriteAction {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.allJdks
          .filter { it.name.startsWith(ANDROID_SDK_NAME_PREFIX) }
          .forEach(jdkTable::removeJdk)
      }
    }
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

  /**
   * The inverse of [canonicalProjectFile]. The Gradle model path can already be canonical, hence the real-path
   * normalization on both sides.
   */
  protected fun projectRelativePath(file: VirtualFile): String =
    projectRoot.toNioPath()
      .toRealPath()
      .relativize(file.toNioPath().toRealPath())
      .invariantSeparatorsPathString

  /**
   * The Gradle fixture restores the snapshotted files on the disk, but it cannot restore an unsaved document of
   * [canonicalProjectFile]: that document belongs to a second virtual file for the same path, and the rollback then
   * fails with a memory-disk conflict.
   */
  protected fun revertUnsavedDocuments() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val unsavedDocuments = fileDocumentManager.unsavedDocuments
    if (unsavedDocuments.isEmpty()) return
    runWriteAction {
      unsavedDocuments.forEach { it.reloadFromDisk() }
    }
  }

  /**
   * If your test case adds extra files to the test data project, override with a matching glob in your test case
   */
  open val additionalSyntaxAndPatterns: Array<String>
    get() = arrayOf()

  private val COMPOSE_RESOURCES_PROJECT = GradleTestFixtureBuilder.create(COMPOSE_RESOURCES_PROJECT_NAME) {
    excludeFilePatterns(
      "glob:**/composeApp/src/commonMain/composeResources/drawable/*.png",
      *additionalSyntaxAndPatterns
    )
    withFile(".gradle/testDataFingerprint", composeResourcesTestDataFingerprint())
    withFiles { projectRoot ->
      val testDataRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(
        composeResourcesProjectRoot()
      ) ?: error("Cannot find $COMPOSE_RESOURCES_PROJECT_NAME test data")
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

  companion object {
    private var longRunningAndroidThreads: Disposable? = null

    /**
     * `AdbLibApplicationService` starts these application-scoped threads when the project opens. They outlive each
     * test, and the thread leak tracker reports them as leaks.
     *
     * `ThreadLeakTracker.longRunningThreadCreated` writes the prefixes into a static set, and it removes them when
     * the parent disposable dies. The parent is therefore a disposable of this class: it lives through every
     * per-test leak check, and it does not hide a leak of `InnocuousThread-` from a later test class.
     */
    @JvmStatic
    @BeforeAll
    fun registerLongRunningAndroidThreads() {
      longRunningAndroidThreads = Disposer.newDisposable("ComposeResourcesLongRunningAndroidThreads").also {
        ThreadLeakTracker.longRunningThreadCreated(it, "AndroidAdbSessionHost", "InnocuousThread-")
      }
    }

    @JvmStatic
    @AfterAll
    fun unregisterLongRunningAndroidThreads() {
      longRunningAndroidThreads?.let(Disposer::dispose)
      longRunningAndroidThreads = null
    }

    private fun composeResourcesTestDataFingerprint(): String {
      val root = composeResourcesProjectRoot()
      val digest = DigestUtil.sha256()
      Files.walk(root).use { paths ->
        paths
          .filter(Files::isRegularFile)
          .filter { path -> isStableTestDataPath(relativeNames(root, path)) }
          .sorted()
          .forEach { path ->
            digest.update(path.relativeTo(root).invariantSeparatorsPathString.toByteArray())
            digest.update(0)
            DigestUtil.updateContentHash(digest, path)
          }
      }
      return DigestUtil.digestToHash(digest)
    }

    private fun relativeNames(root: Path, path: Path): List<String> =
      path.relativeTo(root).map { it.toString() }

    private fun isStableTestDataPath(relativeNames: List<String>): Boolean =
      relativeNames.lastOrNull() != "local.properties" &&
      relativeNames.none { it == "build" || it in GENERATED_TEST_DATA_ROOT_NAMES }

    private val GENERATED_TEST_DATA_ROOT_NAMES = setOf(".gradle", ".idea", ".kotlin", "kotlin-js-store")

    private const val ANDROID_SDK_NAME_PREFIX = "Android "
  }
}
