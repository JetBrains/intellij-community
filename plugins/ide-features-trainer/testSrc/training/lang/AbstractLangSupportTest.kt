// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.lang

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

@TestApplication
internal class AbstractLangSupportTest {
  @Test
  fun `open a fresh learning directory`(@TempDir root: Path, @TestDisposable disposable: Disposable) {
    checkLearningProject(root, disposable, existingProject = false)
  }

  @Test
  fun `open an existing learning directory`(@TempDir root: Path, @TestDisposable disposable: Disposable) {
    checkLearningProject(root, disposable, existingProject = true)
  }

  private fun checkLearningProject(
    root: Path,
    disposable: Disposable,
    existingProject: Boolean,
  ): Unit = timeoutRunBlocking {
    val learningDirectory = root.resolve("demo/IdeaLearningProject").createDirectories()
    learningDirectory.resolve("src").createDirectories()
    if (existingProject) {
      learningDirectory.resolve(Project.DIRECTORY_STORE_FOLDER).createDirectories()
    }
    val virtualDirectory = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(learningDirectory)
                           ?: error("Cannot refresh the learning directory")
    val importer = object : ProjectOpenProcessor() {
      override val name: String = "Enclosing project importer"
      override val isStrongProjectInfoHolder: Boolean = true

      override fun canOpenProject(file: VirtualFile): Boolean = file.toNioPath().startsWith(root)

      override suspend fun openProjectAsync(virtualFile: VirtualFile, projectOpenOptions: ProjectOpenOptions): Project? {
        error("The importer must not open the enclosing project")
      }
    }
    ExtensionTestUtil.maskExtensions(
      ProjectOpenProcessor.EXTENSION_POINT_NAME,
      listOf(importer, PlatformProjectOpenProcessor.getInstance()),
      disposable,
    )
    var callbackProject: Project? = null
    val options = OpenProjectTask {
      forceOpenInNewFrame = true
      preloadServices = false
      beforeOpenTasks += { project ->
        callbackProject = project
        true
      }
    }
    val langSupport = mock<AbstractLangSupport>(defaultAnswer = CALLS_REAL_METHODS)
    val project = withContext(Dispatchers.EDT) {
      langSupport.openOrImportLearningProject(virtualDirectory, options)
    }
    project.useProjectAsync {
      assertEquals(learningDirectory.pathString, it.basePath)
      assertSame(it, callbackProject)
      val contentRoots = readAction {
        ModuleManager.getInstance(it).modules.flatMap { module ->
          ModuleRootManager.getInstance(module).contentRoots.map(VirtualFile::toNioPath)
        }
      }
      assertEquals(listOf(learningDirectory), contentRoots)
    }
  }
}
