// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil

internal class DefaultVcsRootPolicyTest : VcsRootBaseTest() {
  fun `test removed content root loses its mapping`() {
    val member = createMemberContentRoot()
    mapToVcs(member)
    PsiTestUtil.removeContentEntry(rootModule, member)
    assertNotMapped(member)
  }

  fun `test removed content root keeps its mapping when it is the base directory`() {
    getPolicy()
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    mapToVcs(projectRoot)
    PsiTestUtil.removeContentEntry(rootModule, projectRoot)
    assertMapped(projectRoot)
  }

  /** The policy listens to the workspace model from its creation on, so a test creates it before a change. */
  private fun getPolicy(): DefaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(project)

  /** A `member` directory below the project directory that is a content root of [rootModule]. */
  private fun createMemberContentRoot(): VirtualFile {
    val directory = createMemberDirectory()
    PsiTestUtil.addContentRoot(rootModule, directory)
    return directory
  }

  private fun createMemberDirectory(): VirtualFile {
    getPolicy()
    return VfsTestUtil.createDir(projectRoot, "member")
  }

  private fun mapToVcs(directory: VirtualFile) {
    vcsManager.setDirectoryMappings(listOf(VcsDirectoryMapping(directory.path, vcs.name)))
  }

  private fun assertMapped(directory: VirtualFile) {
    assertContainsElements(getMappedDirectories(), directory.path)
  }

  private fun assertNotMapped(directory: VirtualFile) {
    assertDoesntContain(getMappedDirectories(), directory.path)
  }

  private fun getMappedDirectories(): List<String> = vcsManager.getDirectoryMappings().map { it.directory }
}
