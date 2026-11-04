/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.roots

import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsRootError
import com.intellij.openapi.vcs.VcsRootErrorImpl
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class VcsRootErrorsFinderTest {
  private val fixture = vcsRootContextFixture()
  private val context: VcsRootTestContext get() = fixture.get()

  private val PROJECT = VcsDirectoryMapping.PROJECT_CONSTANT.get()

  @Test
  fun `test no roots then no errors`(): Unit = with(context) {
    doTest(VcsRootConfiguration())
  }

  @Test
  fun `test same one root in both then no errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings(".")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test same two roots in both then no errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".", "community")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test one mock root no VCS roots then error`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .unregErrors(".")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test one VCS root no mock roots then error`(): Unit = with(context) {

    val vcsRootConfiguration = VcsRootConfiguration().mappings(".")
      .extraErrors(".")
    doTest(vcsRootConfiguration)
  }


  @Test
  fun `test one root but different then two errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings("community")
      .unregErrors(".").extraErrors("community")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test two roots one matching one different then two errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".", "contrib")
      .unregErrors("community").extraErrors("contrib")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test two roots in mock root one matching in VCS then error`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".")
      .unregErrors("community")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test two roots both not matching then four errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings("another", "contrib")
      .unregErrors("community", ".").extraErrors("contrib", "another")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root no mock roots then error about extra root`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration()
      .mappings(PROJECT)
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root full under mock root then correct`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings(PROJECT)
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root mock root for a content root below project then error`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("content_root")
      .contentRoots("content_root").mappings(PROJECT)
      .unregErrors("content_root")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root mock root below project folder not in a content root then unregistered root error`(): Unit = with(context) {
    // this is to be fixed: auto-detection of MockRoot repositories in subfolders for the <Project> mapping
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("community")
      .contentRoots(".").mappings(PROJECT)
      .unregErrors("community")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root mock root for full project content root linked source folder below project then errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "content_root", "../linked_source_root", "folder")
      .mappings(PROJECT)
      .contentRoots(".", "content_root", "../linked_source_root")
      .unregErrors("content_root", "../linked_source_root", "folder")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root for folder mock root for full project content root linked source folder below project then errors`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "content_root", "../linked_source_root", "folder")
      .mappings(PROJECT, "folder")
      .contentRoots(".", "content_root", "../linked_source_root")
      .unregErrors("content_root", "../linked_source_root")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test project root mock root like in i d e a project then error`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community", "contrib").mappings(PROJECT)
      .contentRoots(".", "community", "contrib").unregErrors("community", "contrib")
    doTest(vcsRootConfiguration)
  }

  @Test
  fun `test real mock root root deeper than three levels should be detected`(): Unit = with(context) {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community", "contrib", "community/level1/level2/level3")
      .contentRoots(".", "community", "contrib").mappings(PROJECT, "community/level1/level2/level3")
      .unregErrors("community", "contrib")
    doTest(vcsRootConfiguration)
  }

  private fun VcsRootTestContext.doTest(vcsRootConfiguration: VcsRootConfiguration) {
    initProject(vcsRootConfiguration)
    addVcsRoots(vcsRootConfiguration.vcsMappings)

    val expected = ArrayList<VcsRootError>()
    expected.addAll(unregAll(vcsRootConfiguration.unregErrors))
    expected.addAll(extraAll(vcsRootConfiguration.extraErrors))
    projectRoot.refresh(false, true)
    val actual = VcsRootErrorsFinder(project).find().filter { error -> error.mapping.vcs == vcs.keyInstanceMethod.name }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected)
  }

  private fun VcsRootTestContext.addVcsRoots(relativeRoots: Collection<String>) {
    for (root in relativeRoots) {
      if (root == PROJECT) {
        vcsManager.setDirectoryMapping("", vcs.name)
      }
      else {
        val absoluteRoot = VcsTestUtil.toAbsolute(root, project)
        vcsManager.setDirectoryMapping(absoluteRoot, vcs.name)
      }
    }
  }

  private fun VcsRootTestContext.unregAll(paths: Collection<String>): Collection<VcsRootError> {
    val unregRoots = ArrayList<VcsRootError>()
    for (path in paths) {
      unregRoots.add(unreg(path))
    }
    return unregRoots
  }

  private fun VcsRootTestContext.extraAll(paths: Collection<String>): Collection<VcsRootError> {
    val extraRoots = ArrayList<VcsRootError>()
    for (path in paths) {
      extraRoots.add(extra(path))
    }
    return extraRoots
  }

  private fun VcsRootTestContext.unreg(path: String): VcsRootError {
    return VcsRootErrorImpl(VcsRootError.Type.UNREGISTERED_ROOT, VcsDirectoryMapping(VcsTestUtil.toAbsolute(path, project), vcs.name))
  }

  private fun VcsRootTestContext.extra(path: String): VcsRootError {
    val mappings = if (PROJECT == path) VcsDirectoryMapping.createDefault(vcs.name)
    else VcsDirectoryMapping(VcsTestUtil.toAbsolute(path, project), vcs.name)
    return VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, mappings)
  }
}
