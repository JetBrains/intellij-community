// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.dvcs.ignore.IgnoredToExcludeNotificationProvider
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
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
    val ignoredHolderWaiter = repo.untrackedFilesHolder.createWaiter()

    val gitIgnore = file(GITIGNORE).create(gitignoreContent)
    VfsUtil.findFileByIoFile(gitIgnore.file, true) //trigger VFS create event explicitly

    ignoredHolderWaiter.waitFor()
  }

  private fun assertNotificationByContent(notificationContent: String) {
    val gitignore = file(GITIGNORE)
    val gitIgnoreVF = getVirtualFile(gitignore.file)
    val editor =
      invokeAndWaitIfNeeded { FileEditorManager.getInstance(project).openFile(gitIgnoreVF, false) }.firstOrNull()

    assertNotNull("Editor for $gitignore not found", editor)

    val notificationPanel = IgnoredToExcludeNotificationProvider().collectNotificationData(project, gitIgnoreVF)?.apply(editor!!) as? EditorNotificationPanel
    assertTrue("Notification $notificationContent not found", notificationPanel?.text == notificationContent)
  }

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