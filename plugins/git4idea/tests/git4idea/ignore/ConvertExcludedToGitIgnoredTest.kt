// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.test.GitSingleRepoTest
import java.io.File
import java.util.concurrent.TimeUnit

class ConvertExcludedToGitIgnoredTest : GitSingleRepoTest() {
  private lateinit var moduleContentRoot: VirtualFile
  private lateinit var gitIgnore: File

  override fun isCreateDirectoryBasedProject() = true

  override fun setUp() {
    super.setUp()
    VcsApplicationSettings.getInstance().MANAGE_IGNORE_FILES = true
    Registry.get("vcs.ignorefile.generation").setValue(true, testRootDisposable)
    Registry.get("ide.hide.excluded.files").setValue(false, testRootDisposable)
    gitIgnore = File("$projectPath/$GITIGNORE")
  }

  override fun setUpProject() {
    super.setUpProject()
    // will create .idea directory
    runBlockingMaybeCancellable {
      saveSettings(project)
    }
  }

  override fun setUpModule() {
    ApplicationManager.getApplication().runWriteAction {
      myModule = createMainModule()
      moduleContentRoot = getOrCreateProjectBaseDir()
      myModule.addContentRoot(moduleContentRoot)
    }
  }

  fun testExcludedFolder() {
    val excluded = createChildDirectory(moduleContentRoot, "exc")
    createChildData(excluded, "excluded.txt") //Don't mark empty directories like ignored since versioning such directories not supported in Git
    PsiTestUtil.addExcludedRoot(myModule, excluded)

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /exc/
    """)

    assertFalse(changeListManager.isIgnoredFile(moduleContentRoot))
    assertTrue(changeListManager.isIgnoredFile(excluded))
  }

  fun testModuleOutput() {
    val output = createChildDirectory(moduleContentRoot, "out")
    PsiTestUtil.setCompilerOutputPath(myModule, output.url, false)
    createChildData(output, "out.class")

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /out/
    """)

    assertFalse(changeListManager.isIgnoredFile(moduleContentRoot))
    assertTrue(changeListManager.isIgnoredFile(output))
  }

  fun testProjectOutput() {
    val output = createChildDirectory(projectRoot, "projectOutput")
    createChildData(output, "out.class")
    CompilerProjectExtension.getInstance(project)!!.compilerOutputUrl = output.url

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /projectOutput/
    """)

    assertTrue(changeListManager.isIgnoredFile(output))
  }

  fun testModuleOutputUnderProjectOutput() {
    val output = createChildDirectory(projectRoot, "projectOutput")
    createChildData(output, "out.class")
    CompilerProjectExtension.getInstance(project)!!.compilerOutputUrl = output.url
    val moduleOutput = createChildDirectory(output, "module")
    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutput.url, false)

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /projectOutput/
    """)

    assertTrue(changeListManager.isIgnoredFile(output))
    assertTrue(changeListManager.isIgnoredFile(moduleOutput))
  }

  fun testModuleOutputUnderExcluded() {
    val excluded = createChildDirectory(moduleContentRoot, "target")
    createChildData(excluded, "out.class")
    PsiTestUtil.addExcludedRoot(myModule, excluded)
    val moduleOutput = createChildDirectory(excluded, "classes")
    createChildData(moduleOutput, "out.class")
    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutput.url, false)

    generateIgnoreFileAndWaitHoldersUpdate()
    assertGitignoreValid(gitIgnore, """
    # Project exclude paths
    /target/
    """)

    assertTrue(changeListManager.isIgnoredFile(excluded))
    assertTrue(changeListManager.isIgnoredFile(moduleOutput))
  }

  fun testDoNotIgnoreInnerModuleExplicitlyMarkedAsExcludedFromOuterModule() {
    val inner = createChildDirectory(moduleContentRoot, "inner")
    createChildData(inner, "inner.txt")
    PsiTestUtil.addModule(myProject, ModuleType.EMPTY, "inner", inner)
    PsiTestUtil.addExcludedRoot(myModule, inner)

    assertFalse(changeListManager.isIgnoredFile(inner))
  }

  private fun generateIgnoreFileAndWaitHoldersUpdate() {
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
    flushIgnoreHoldersQueue()
    val waiter = repo.untrackedFilesHolder.createWaiter()
    VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs, projectRoot)
    waiter.waitFor()
  }

  private fun flushIgnoreHoldersQueue() {
    VcsIgnoreManagerImpl.getInstanceImpl(project).ignoreRefreshQueue.waitForAllExecuted(10, TimeUnit.MINUTES)
  }
}