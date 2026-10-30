// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.workspaceModel.ide.registerProjectRootBlocking
import com.intellij.workspaceModel.ide.unregisterProjectRootBlocking
import java.nio.file.Files

internal class DefaultVcsRootPolicyTest : VcsRootBaseTest() {
  fun `test project roots replace the base directory when the registry key is on`() {
    val member = createMemberProjectRoot()
    turnProjectRootsKeyOn()
    val roots = getDefaultRoots()
    assertContainsElements(roots, member)
    assertDoesntContain(roots, projectRoot)
  }

  fun `test project roots are not default roots when the registry key is off`() {
    val member = createMemberProjectRoot()
    val roots = getDefaultRoots()
    assertDoesntContain(roots, member)
    assertContainsElements(roots, projectRoot)
  }

  fun `test the idea directory is a default root only when the registry key is off`() {
    val ideaDir = createIdeaDirectory()
    assertContainsElements(getDefaultRoots(), ideaDir)
    turnProjectRootsKeyOn()
    assertDoesntContain(getDefaultRoots(), ideaDir)
  }

  fun `test a content root is a default root whatever the registry key says`() {
    val member = createMemberContentRoot()
    assertContainsElements(getDefaultRoots(), member)
    turnProjectRootsKeyOn()
    assertContainsElements(getDefaultRoots(), member)
  }

  fun `test removed content root keeps its mapping when a project root supplies it`() {
    turnProjectRootsKeyOn()
    val member = createMemberProjectRoot()
    PsiTestUtil.addContentRoot(rootModule, member)
    mapToVcs(member)
    PsiTestUtil.removeContentEntry(rootModule, member)
    assertMapped(member)
  }

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

  fun `test removed project root keeps its mapping when a content root supplies it`() {
    turnProjectRootsKeyOn()
    val member = createMemberContentRoot()
    registerProjectRootBlocking(project, member.toNioPath())
    mapToVcs(member)
    unregisterProjectRootBlocking(project, member.toNioPath())
    assertMapped(member)
  }

  fun `test removed project root loses its mapping`() {
    turnProjectRootsKeyOn()
    val member = createMemberProjectRoot()
    mapToVcs(member)
    unregisterProjectRootBlocking(project, member.toNioPath())
    assertNotMapped(member)
  }

  fun `test removed project root keeps its mapping when the registry key is off`() {
    val member = createMemberProjectRoot()
    mapToVcs(member)
    unregisterProjectRootBlocking(project, member.toNioPath())
    assertMapped(member)
  }

  /** Turns the registry key on for this test. The key is declared in `VcsExtensions.xml`, which the test loads. */
  private fun turnProjectRootsKeyOn() {
    Registry.get(DefaultVcsRootPolicy.PROJECT_ROOTS_REGISTRY_KEY).setValue(true, testRootDisposable)
  }

  private fun getDefaultRoots(): Collection<VirtualFile> = getPolicy().getDefaultVcsRoots()

  /** The policy listens to the workspace model from its creation on, so a test creates it before a change. */
  private fun getPolicy(): DefaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(project)

  /** The `.idea` directory of the test project, created on disk and in the VFS. */
  private fun createIdeaDirectory(): VirtualFile {
    val path = checkNotNull(project.stateStore.directoryStorePath) { "The test project is directory based" }
    Files.createDirectories(path)
    return checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "No VFS entry for $path" }
  }

  /** A `member` directory below the project directory that is a `ProjectRootEntity`. */
  private fun createMemberProjectRoot(): VirtualFile {
    val directory = createMemberDirectory()
    registerProjectRootBlocking(project, directory.toNioPath())
    return directory
  }

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
