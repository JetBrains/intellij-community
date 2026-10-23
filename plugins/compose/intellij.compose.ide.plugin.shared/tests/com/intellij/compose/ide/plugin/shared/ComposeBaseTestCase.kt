// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.util.io.DigestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * The base class for a code insight test on a Compose Multiplatform Gradle project from the test data.
 *
 * The test copies the project at [testDataProjectRoot] and imports it with Gradle [targetGradleVersion].
 * The test runs once for each source set name. Put a `@ValueSource` annotation with the source set names on the
 * test class. [sourceSetName] holds the current name.
 */
@ParameterizedClass(name = "source set {0}")
@GradleProjectTestApplication
abstract class ComposeBaseTestCase : GradleCodeInsightBaseTestCase() {
  @Parameter
  protected lateinit var sourceSetName: String

  /** The absolute root of the Gradle project in the test data. The directory name is also the name of the project. */
  protected abstract val testDataProjectRoot: Path

  /** The Gradle version that imports the project. */
  protected open val targetGradleVersion: String = TARGET_GRADLE_VERSION

  /** The glob patterns of the test data project files that the test can change without an error. */
  protected open val excludedFilePatterns: Array<String>
    get() = arrayOf()

  /**
   * The extra glob patterns of one test case. They add to [excludedFilePatterns].
   * If your test case adds extra files to the test data project, override with a matching glob in your test case.
   */
  protected open val additionalSyntaxAndPatterns: Array<String>
    get() = arrayOf()

  /**
   * An optional step that configures the copy of the test data project.
   * Override it to apply a step that only some projects need, such as [withAndroidSdk].
   */
  protected open fun FileTestFixture.Builder.configureProject() {}

  override fun tearDown() {
    runAll(
      { KotlinSdkType.removeKotlinSdkInTests() },
      { removeAndroidSdksInTests() },
      { super.tearDown() }
    )
  }

  /**
   * The Gradle sync creates an Android SDK from the local Android SDK path, and the SDK leak tracker reports it.
   * The name prefix is `com.android.tools.idea.sdk.AndroidSdks.SDK_NAME_PREFIX`, which this module cannot depend on.
   */
  private fun removeAndroidSdksInTests() {
    invokeAndWaitIfNeeded {
      runWriteAction {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.allJdks
          .filter { it.name.startsWith(ANDROID_SDK_NAME_PREFIX) }
          .forEach(jdkTable::removeJdk)
      }
    }
  }

  protected fun testComposeProject(test: () -> Unit) {
    test(GradleVersion.version(targetGradleVersion), composeProject, test)
  }

  /**
   * `VirtualFile.getCanonicalFile` is not enough here: it resolves through the VFS without refreshing, so it returns
   * `null` while the real path (`/var` vs `/private/var` on macOS) is not in the VFS yet.
   */
  protected fun canonicalProjectFile(relativePath: String): VirtualFile =
    getFile(relativePath).let { file ->
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file.toNioPath().toRealPath()) ?: file
    }

  protected fun snapshotProjectFile(relativePath: String) {
    gradleFixture.fileFixture.snapshot(relativePath)
  }

  /**
   * The inverse of [canonicalProjectFile]. The project root is already a real path.
   */
  protected fun projectRelativePath(file: VirtualFile): String =
    projectRoot.toNioPath()
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
    invokeAndWaitIfNeeded {
      runWriteAction {
        unsavedDocuments.forEach { it.reloadFromDisk() }
      }
    }
  }

  /** The builder is lazy, because a subclass initializes its properties after this class. */
  private val composeProject: GradleTestFixtureBuilder by lazy {
    val testDataRootPath = testDataProjectRoot
    val projectName = testDataRootPath.name
    GradleTestFixtureBuilder.create(projectName) {
      excludeFilePatterns(*excludedFilePatterns, *additionalSyntaxAndPatterns)
      withFile(".gradle/testDataFingerprint", testDataFingerprint(testDataRootPath))
      configureProject()
      withFiles { projectRoot ->
        val testDataRoot = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(testDataRootPath)
                           ?: error("Cannot find $projectName test data")
        edtWriteAction {
          VfsUtil.copyDirectory(this, testDataRoot, projectRoot) { file ->
            val relativePath = requireNotNull(VfsUtil.getRelativePath(file, testDataRoot)) {
              "$file is not under $testDataRoot"
            }
            isStableTestDataPath(relativePath.split('/'))
          }
        }
      }
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
      longRunningAndroidThreads = Disposer.newDisposable("ComposeLongRunningAndroidThreads").also {
        ThreadLeakTracker.longRunningThreadCreated(it, "AndroidAdbSessionHost", "InnocuousThread-")
      }
    }

    @JvmStatic
    @AfterAll
    fun unregisterLongRunningAndroidThreads() {
      longRunningAndroidThreads?.let(Disposer::dispose)
      longRunningAndroidThreads = null
    }

    private fun testDataFingerprint(root: Path): String {
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
