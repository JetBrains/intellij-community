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
import com.intellij.util.containers.ContainerUtil
import java.util.*


class VcsRootErrorsFinderTest : VcsRootBaseTest() {

  private val PROJECT = VcsDirectoryMapping.PROJECT_CONSTANT.get()
  
  fun `test no roots then no errors`() {
    doTest(VcsRootConfiguration())
  }

  fun `test same one root in both then no errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings(".")
    doTest(vcsRootConfiguration)
  }

  fun `test same two roots in both then no errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".", "community")
    doTest(vcsRootConfiguration)
  }

  fun `test one mock root no VCS roots then error`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .unregErrors(".")
    doTest(vcsRootConfiguration)
  }

  fun `test one VCS root no mock roots then error`() {

    val vcsRootConfiguration = VcsRootConfiguration().mappings(".")
      .extraErrors(".")
    doTest(vcsRootConfiguration)
  }


  fun `test one root but different then two errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings("community")
      .unregErrors(".").extraErrors("community")
    doTest(vcsRootConfiguration)
  }

  fun `test two roots one matching one different then two errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".", "contrib")
      .unregErrors("community").extraErrors("contrib")
    doTest(vcsRootConfiguration)
  }

  fun `test two roots in mock root one matching in VCS then error`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".")
      .unregErrors("community")
    doTest(vcsRootConfiguration)
  }

  fun `test two roots both not matching then four errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings("another", "contrib")
      .unregErrors("community", ".").extraErrors("contrib", "another")
    doTest(vcsRootConfiguration)
  }

  fun `test project root no mock roots then error about extra root`() {
    val vcsRootConfiguration = VcsRootConfiguration()
      .mappings(PROJECT)
    doTest(vcsRootConfiguration)
  }

  fun `test project root full under mock root then correct`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings(PROJECT)
    doTest(vcsRootConfiguration)
  }

  fun `test project root mock root for a content root below project then error`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("content_root")
      .contentRoots("content_root").mappings(PROJECT)
      .unregErrors("content_root")
    doTest(vcsRootConfiguration)
  }

  fun `test project root mock root below project folder not in a content root then unregistered root error`() {
    // this is to be fixed: auto-detection of MockRoot repositories in subfolders for the <Project> mapping
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("community")
      .contentRoots(".").mappings(PROJECT)
      .unregErrors("community")
    doTest(vcsRootConfiguration)
  }

  fun `test project root mock root for full project content root linked source folder below project then errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "content_root", "../linked_source_root", "folder")
      .mappings(PROJECT)
      .contentRoots(".", "content_root", "../linked_source_root")
      .unregErrors("content_root", "../linked_source_root", "folder")
    doTest(vcsRootConfiguration)
  }

  fun `test project root for folder mock root for full project content root linked source folder below project then errors`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "content_root", "../linked_source_root", "folder")
      .mappings(PROJECT, "folder")
      .contentRoots(".", "content_root", "../linked_source_root")
      .unregErrors("content_root", "../linked_source_root")
    doTest(vcsRootConfiguration)
  }

  fun `test project root mock root like in i d e a project then error`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community", "contrib").mappings(PROJECT)
      .contentRoots(".", "community", "contrib").unregErrors("community", "contrib")
    doTest(vcsRootConfiguration)
  }

  fun `test real mock root root deeper than three levels should be detected`() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community", "contrib", "community/level1/level2/level3")
      .contentRoots(".", "community", "contrib").mappings(PROJECT, "community/level1/level2/level3")
      .unregErrors("community", "contrib")
    doTest(vcsRootConfiguration)
  }

  private fun doTest(vcsRootConfiguration: VcsRootConfiguration) {
    initProject(vcsRootConfiguration)
    addVcsRoots(vcsRootConfiguration.vcsMappings)

    val expected = ArrayList<VcsRootError>()
    expected.addAll(unregAll(vcsRootConfiguration.unregErrors))
    expected.addAll(extraAll(vcsRootConfiguration.extraErrors))
    projectRoot.refresh(false, true)
    val actual = ContainerUtil.filter(VcsRootErrorsFinder(myProject).find()) { error -> error.mapping.vcs == vcs.keyInstanceMethod.name }
    VcsTestUtil.assertEqualCollections(actual, expected)
  }

  internal fun addVcsRoots(relativeRoots: Collection<String>) {
    for (root in relativeRoots) {
      if (root == PROJECT) {
        vcsManager.setDirectoryMapping("", vcs.name)
      }
      else {
        val absoluteRoot = VcsTestUtil.toAbsolute(root, myProject)
        vcsManager.setDirectoryMapping(absoluteRoot, vcs.name)
      }
    }
  }

  internal fun unregAll(paths: Collection<String>): Collection<VcsRootError> {
    val unregRoots = ArrayList<VcsRootError>()
    for (path in paths) {
      unregRoots.add(unreg(path))
    }
    return unregRoots
  }

  internal fun extraAll(paths: Collection<String>): Collection<VcsRootError> {
    val extraRoots = ArrayList<VcsRootError>()
    for (path in paths) {
      extraRoots.add(extra(path))
    }
    return extraRoots
  }

  private fun unreg(path: String): VcsRootError {
    return VcsRootErrorImpl(VcsRootError.Type.UNREGISTERED_ROOT, VcsDirectoryMapping(VcsTestUtil.toAbsolute(path, myProject), vcs.name))
  }

  private fun extra(path: String): VcsRootError {
    val mappings = if (PROJECT == path) VcsDirectoryMapping.createDefault(vcs.name)
    else VcsDirectoryMapping(VcsTestUtil.toAbsolute(path, myProject), vcs.name)
    return VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, mappings)
  }
}
