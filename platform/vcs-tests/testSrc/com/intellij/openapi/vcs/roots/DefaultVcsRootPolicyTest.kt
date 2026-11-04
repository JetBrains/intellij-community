// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.workspaceModel.ide.registerProjectRootBlocking
import com.intellij.workspaceModel.ide.unregisterProjectRootBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
internal class DefaultVcsRootPolicyTest {
  private val fixture = vcsRootContextFixture()
  private val context: VcsRootTestContext get() = fixture.get()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `test project roots replace the base directory when the registry key is on`(): Unit = with(context) {
    val member = createMemberProjectRoot()
    turnProjectRootsKeyOn()
    val roots = getDefaultRoots()
    assertThat(roots).contains(member)
    assertThat(roots).doesNotContain(projectRoot)
  }

  @Test
  fun `test project roots are not default roots when the registry key is off`(): Unit = with(context) {
    val member = createMemberProjectRoot()
    val roots = getDefaultRoots()
    assertThat(roots).doesNotContain(member)
    assertThat(roots).contains(projectRoot)
  }

  @Test
  fun `test the idea directory is a default root only when the registry key is off`(): Unit = with(context) {
    val ideaDir = createIdeaDirectory()
    assertThat(getDefaultRoots()).contains(ideaDir)
    turnProjectRootsKeyOn()
    assertThat(getDefaultRoots()).doesNotContain(ideaDir)
  }

  @Test
  fun `test a content root is a default root whatever the registry key says`(): Unit = with(context) {
    val member = createMemberContentRoot()
    assertThat(getDefaultRoots()).contains(member)
    turnProjectRootsKeyOn()
    assertThat(getDefaultRoots()).contains(member)
  }

  @Test
  fun `test removed content root keeps its mapping when a project root supplies it`(): Unit = with(context) {
    turnProjectRootsKeyOn()
    val member = createMemberProjectRoot()
    PsiTestUtil.addContentRoot(rootModule, member)
    mapToVcs(member)
    PsiTestUtil.removeContentEntry(rootModule, member)
    assertMapped(member)
  }

  @Test
  fun `test removed content root loses its mapping`(): Unit = with(context) {
    val member = createMemberContentRoot()
    mapToVcs(member)
    PsiTestUtil.removeContentEntry(rootModule, member)
    assertNotMapped(member)
  }

  @Test
  fun `test removed content root keeps its mapping when it is the base directory`(): Unit = with(context) {
    getPolicy()
    PsiTestUtil.addContentRoot(rootModule, projectRoot)
    mapToVcs(projectRoot)
    PsiTestUtil.removeContentEntry(rootModule, projectRoot)
    assertMapped(projectRoot)
  }

  @Test
  fun `test removed project root keeps its mapping when a content root supplies it`(): Unit = with(context) {
    turnProjectRootsKeyOn()
    val member = createMemberContentRoot()
    registerProjectRootBlocking(project, member.toNioPath())
    mapToVcs(member)
    unregisterProjectRootBlocking(project, member.toNioPath())
    assertMapped(member)
  }

  @Test
  fun `test removed project root loses its mapping`(): Unit = with(context) {
    turnProjectRootsKeyOn()
    val member = createMemberProjectRoot()
    mapToVcs(member)
    unregisterProjectRootBlocking(project, member.toNioPath())
    assertNotMapped(member)
  }

  @Test
  fun `test removed project root keeps its mapping when the registry key is off`(): Unit = with(context) {
    val member = createMemberProjectRoot()
    mapToVcs(member)
    unregisterProjectRootBlocking(project, member.toNioPath())
    assertMapped(member)
  }

  /** Turns the registry key on for this test. The key is declared in `VcsExtensions.xml`, which the test loads. */
  private fun turnProjectRootsKeyOn() {
    Registry.get(DefaultVcsRootPolicy.PROJECT_ROOTS_REGISTRY_KEY).setValue(true, disposable)
  }

  private fun VcsRootTestContext.getDefaultRoots(): Collection<VirtualFile> = getPolicy().getDefaultVcsRoots()

  /** The policy listens to the workspace model from its creation on, so a test creates it before a change. */
  private fun VcsRootTestContext.getPolicy(): DefaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(project)

  /** The `.idea` directory of the test project, created on disk and in the VFS. */
  private fun VcsRootTestContext.createIdeaDirectory(): VirtualFile {
    val path = checkNotNull(project.stateStore.directoryStorePath) { "The test project is directory based" }
    Files.createDirectories(path)
    return checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "No VFS entry for $path" }
  }

  /** A `member` directory below the project directory that is a `ProjectRootEntity`. */
  private fun VcsRootTestContext.createMemberProjectRoot(): VirtualFile {
    val directory = createMemberDirectory()
    registerProjectRootBlocking(project, directory.toNioPath())
    return directory
  }

  /** A `member` directory below the project directory that is a content root of [rootModule]. */
  private fun VcsRootTestContext.createMemberContentRoot(): VirtualFile {
    val directory = createMemberDirectory()
    PsiTestUtil.addContentRoot(rootModule, directory)
    return directory
  }

  private fun VcsRootTestContext.createMemberDirectory(): VirtualFile {
    getPolicy()
    return VfsTestUtil.createDir(projectRoot, "member")
  }

  private fun VcsRootTestContext.mapToVcs(directory: VirtualFile) {
    vcsManager.setDirectoryMappings(listOf(VcsDirectoryMapping(directory.path, vcs.name)))
  }

  private fun VcsRootTestContext.assertMapped(directory: VirtualFile) {
    assertThat(getMappedDirectories()).contains(directory.path)
  }

  private fun VcsRootTestContext.assertNotMapped(directory: VirtualFile) {
    assertThat(getMappedDirectories()).doesNotContain(directory.path)
  }

  private fun VcsRootTestContext.getMappedDirectories(): List<String> = vcsManager.getDirectoryMappings().map { it.directory }
}
