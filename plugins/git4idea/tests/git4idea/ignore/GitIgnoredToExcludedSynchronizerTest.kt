// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest

const val GEN = "gen"

class GitIgnoredToExcludedSynchronizerTest : GitSingleRepoTest() {

  private lateinit var out: VirtualFile
  private lateinit var excluded: VirtualFile
  private lateinit var gen: VirtualFile

  override fun setUp() {
    super.setUp()
    Registry.get("vcs.enable.add.ignored.directories.to.exclude").setValue(true, testRootDisposable)
    Registry.get("vcs.propose.add.ignored.directories.to.exclude").setValue(true, testRootDisposable)
  }

  override fun setUpModule() {
    runWriteAction {
      myModule = createMainModule()
      val moduleDir = getOrCreateModuleDir(module)
      myModule.addContentRoot(moduleDir)

      //create file in dirs, otherwise the directory will be not treated as ignored by Git
      out = moduleDir.findOrCreateDir(OUT).apply { createFile("a.class") }
      excluded = moduleDir.findOrCreateDir(EXCLUDED).apply { createFile("b.class") }
      gen = moduleDir.findOrCreateDir(GEN).apply { createFile("a.java") }
      myModule.addSourceFolder(gen)
    }
  }

  fun `test mark ignored directories as excluded notification`() {
    assertEmpty(module.excludes())

    createGitignoreAndWait("""
                            /$EXCLUDED/
                            /$OUT/
                           """.trimIndent())

    assertNotificationByContent(VcsBundle.message("ignore.to.exclude.notification.message"))
  }

  fun `test mark ignored directories as excluded`() {
    VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED = true

    assertEmpty(module.excludes())

    createGitignoreAndWait("""
                            /$EXCLUDED/
                            /$OUT/
                           """.trimIndent())

    assertExcludedDirs(out, excluded)
  }

  fun `test do not mark ignored source root directory as excluded`() {
    VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED = true

    assertSourceDirs(gen)

    createGitignoreAndWait("""
                            /$EXCLUDED/
                            /$OUT/
                            /$GEN/
                           """.trimIndent())

    assertExcludedDirs(out, excluded)
    assertSourceDirs(gen)
  }

  private fun createGitignoreAndWait(gitignoreContent: String) {
    val ignoredHolderWaiter = (repo.ignoredFilesHolder as VcsRepositoryIgnoredFilesHolderBase<*>).createWaiter()

    val gitIgnore = file(GITIGNORE).create(gitignoreContent)
    VfsUtil.findFileByIoFile(gitIgnore.file, true) //trigger VFS create event explicitly

    ignoredHolderWaiter.waitFor()
  }

  private fun assertNotificationByContent(notificationContent: String) =
    vcsNotifier.notifications.find { it.content == notificationContent }
    ?: fail("Notification $notificationContent not found")

  private fun assertExcludedDirs(vararg expectedExcludes: VirtualFile) {
    val excludes = module.excludes()
    assertContainsElements(excludes, *expectedExcludes)
  }

  private fun assertSourceDirs(vararg expectedSources: VirtualFile) {
    val sourceRoots = module.sourceRoots()
    assertContainsElements(sourceRoots, *expectedSources)
  }

  private fun Module.sourceRoots() = invokeAndWaitIfNeeded { ModuleRootManager.getInstance(this).sourceRoots.toList() }
  private fun Module.excludes() = invokeAndWaitIfNeeded { ModuleRootManager.getInstance(this).excludeRoots.toList() }
}