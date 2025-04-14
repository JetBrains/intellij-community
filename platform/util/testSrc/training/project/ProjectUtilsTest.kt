// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.project

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import training.lang.LangManager
import training.lang.LangSupport
import java.nio.file.Path
import kotlin.io.path.*

@TestApplication
class ProjectUtilsTest {
  private companion object {
    private const val LANG = "FakeLang"
  }

  // Ensure that a project is cleared, but protected files aren't removed
  @Test
  fun testRestoreProject(@TempDir root: Path): Unit = timeoutRunBlocking {
    withContext(Dispatchers.IO) {
      val protectedFile = root.resolve("excludedDir").resolve("excludedFile").createParentDirectories().createFile()

      val lang = Mockito.mock<LangSupport>()
      `when`(lang.getLearningProjectPath(any())).thenReturn(root)
      `when`(lang.getContentRootPath(any())).thenReturn(root)
      `when`(lang.getProtectedDirs(any())).thenReturn(setOf(protectedFile.parent))
      `when`(lang.primaryLanguage).thenReturn(LANG)
      `when`(lang.contentRootDirectoryName).thenReturn(LANG)

      val badFile = root.resolve("junk_folder").resolve("file.txt").createParentDirectories().createFile()
      val goodFiles = arrayOf(
        protectedFile,
        root.resolve("venv").createDirectory(),
        root.resolve(".git").resolve("file").createParentDirectories().createFile())

      LangManager.getInstance().setLearningProjectPath(lang, root.pathString)
      ProjectUtils.restoreProject(lang, ProjectManager.getInstance().defaultProject)
      assertFalse(badFile.exists(), "$badFile should have been deleted")
      for (goodFile in goodFiles) {
        assertTrue(goodFile.exists(), "$goodFile shouldn't have been deleted")
      }
    }
  }
}