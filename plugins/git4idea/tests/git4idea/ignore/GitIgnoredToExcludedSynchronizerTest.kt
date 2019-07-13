// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest
import org.junit.Assume.assumeTrue

class GitIgnoredToExcludedSynchronizerTest : GitSingleRepoTest() {

  private lateinit var out: VirtualFile
  private lateinit var excluded: VirtualFile

  override fun setUp() {
    super.setUp()
    assumeTrue(VcsApplicationSettings.getInstance().MARK_IGNORED_AS_EXCLUDED)
  }

  override fun setUpModule() {
    runWriteAction {
      myModule = createMainModule()
      val moduleDir = getOrCreateModuleDir(module)
      myModule.addContentRoot(moduleDir)

      //create file in dirs, otherwise the directory will be not treated as ignored by Git
      out = moduleDir.findOrCreateDir(OUT).apply { createFile("a.class") }
      excluded = moduleDir.findOrCreateDir(EXCLUDED).apply { createFile("b.class") }
    }
  }

  fun `test mark ignored directories as excluded`() {
    val ignoredHolderWaiter = (repo.ignoredFilesHolder as VcsRepositoryIgnoredFilesHolderBase<*>).createWaiter()

    file(GITIGNORE)
      .create("""
                /$EXCLUDED/
                /$OUT/
              """.trimIndent())

    ignoredHolderWaiter.waitFor()

    assertExcludedDirs(out, excluded)
  }

  private fun assertExcludedDirs(vararg expectedExcludes: VirtualFile) {
    val excludes = invokeAndWaitIfNeeded { ModuleRootManager.getInstance(module).excludeRoots.toList() }
    assertContainsElements(excludes, *expectedExcludes)
  }
}