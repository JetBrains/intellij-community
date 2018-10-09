// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import git4idea.GitUtil
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository
import java.io.File

const val OUT = "out"
const val EXCLUDED = "excluded"
const val EXCLUDED_CHILD = "child"
const val EXCLUDED_CHILD_DIR = "$EXCLUDED/$EXCLUDED_CHILD"

class GitIgnoredFileTest : GitPlatformTest() {

  override fun setUp() {
    super.setUp()
    createRepository(project, projectPath)
    GitUtil.generateGitignoreFileIfNeeded(project, projectRoot)
  }

  override fun setUpModule() {
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      myModule = createMainModule()
      val moduleDir = myModule.moduleFile!!.parent
      myModule.addContentRoot(moduleDir)
      val outDir = moduleDir.findOrCreateDir(OUT)
      val excludedDir = moduleDir.findOrCreateDir(EXCLUDED)
      val excludedDirChild = excludedDir.findOrCreateDir(EXCLUDED_CHILD)
      myModule.addExclude(outDir)
      myModule.addExclude(excludedDir)
      myModule.addExclude(excludedDirChild)
    }
  }

  fun `test gitignore created`() {
    assertTrue(File("$projectPath/$GITIGNORE").exists())
  }

  fun `test gitignore content`() {
    val projectCharset = EncodingProjectManager.getInstance(project).defaultCharset
    val gitIgnoreExpectedContentList = """
        # Default ignored files
        /.shelf/
        *.iws

        # Project exclude paths
        /$EXCLUDED/
        /$EXCLUDED_CHILD_DIR/
        /$OUT/
    """.trimIndent().lines()
    val gitIgnoreFile = File("$projectPath/$GITIGNORE")
    assertTrue(gitIgnoreFile.exists())
    val generatedGitIgnoreContent = gitIgnoreFile.readText(projectCharset)
    assertFalse("Generated ignore file is empty", generatedGitIgnoreContent.isBlank())
    assertFalse("Generated ignore file content should be system-independent", generatedGitIgnoreContent.contains('\\'))
    assertContainsOrdered(generatedGitIgnoreContent.lines(), gitIgnoreExpectedContentList)
  }

  private fun VirtualFile.findOrCreateDir(dirName: String) = this.findChild(dirName) ?: createChildDirectory(this, dirName)
}