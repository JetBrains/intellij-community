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
import java.io.IOException
import java.util.*


/**
 * @author Nadya Zabrodina
 */
class VcsRootErrorsFinderTest : VcsRootBaseTest() {

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
  }

  @Throws(IOException::class)
  fun testNoRootsThenNoErrors() {
    doTest(VcsRootConfiguration())
  }

  @Throws(IOException::class)
  fun testSameOneRootInBothThenNoErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings(".")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testSameTwoRootsInBothThenNoErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".", "community")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testOneMockRootNoVCSRootsThenError() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .unregErrors(".")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testOneVCSRootNoMockRootsThenError() {

    val vcsRootConfiguration = VcsRootConfiguration().mappings(".")
      .extraErrors(".")
    doTest(vcsRootConfiguration)
  }


  @Throws(IOException::class)
  fun testOneRootButDifferentThenTwoErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings("community")
      .unregErrors(".").extraErrors("community")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testTwoRootsOneMatchingOneDifferentThenTwoErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".", "contrib")
      .unregErrors("community").extraErrors("contrib")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testTwoRootsInMockRootOneMatchingInVCSThenError() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings(".")
      .unregErrors("community")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testTwoRootsBothNotMatchingThenFourErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community")
      .mappings("another", "contrib")
      .unregErrors("community", ".").extraErrors("contrib", "another")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootNoMockRootsThenErrorAboutExtraRoot() {
    val vcsRootConfiguration = VcsRootConfiguration()
      .mappings(PROJECT)
      .extraErrors(PROJECT)
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootFullUnderMockRootThenCorrect() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".")
      .mappings(PROJECT)
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootMockRootForAContentRootBelowProjectThenError() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("content_root")
      .contentRoots("content_root").mappings(PROJECT)
      .unregErrors("content_root").extraErrors(PROJECT)
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootMockRootBelowProjectFolderNotInAContentRootThenUnregisteredRootError() {
    // this is to be fixed: auto-detection of MockRoot repositories in subfolders for the <Project> mapping
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots("community")
      .contentRoots(".").mappings(PROJECT)
      .unregErrors("community").extraErrors(PROJECT)
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootMockRootForFullProjectContentRootLinkedSourceFolderBelowProjectThenErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "content_root", "../linked_source_root", "folder")
      .mappings(PROJECT)
      .contentRoots(".", "content_root", "../linked_source_root")
      .unregErrors("content_root", "../linked_source_root", "folder")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootForFolderMockRootForFullProjectContentRootLinkedSourceFolderBelowProjectThenErrors() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "content_root", "../linked_source_root", "folder")
      .mappings(PROJECT, "folder")
      .contentRoots(".", "content_root", "../linked_source_root")
      .unregErrors("content_root", "../linked_source_root")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testProjectRootMockRootLikeInIDEAProjectThenError() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community", "contrib").mappings(PROJECT)
      .contentRoots(".", "community", "contrib").unregErrors("community", "contrib")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  fun testRealMockRootRootDeeperThanThreeLevelsShouldBeDetected() {
    val vcsRootConfiguration = VcsRootConfiguration().vcsRoots(".", "community", "contrib", "community/level1/level2/level3")
      .contentRoots(".", "community", "contrib").mappings(PROJECT, "community/level1/level2/level3")
      .unregErrors("community", "contrib")
    doTest(vcsRootConfiguration)
  }

  @Throws(IOException::class)
  private fun doTest(vcsRootConfiguration: VcsRootConfiguration) {
    initProject(vcsRootConfiguration)
    addVcsRoots(vcsRootConfiguration.vcsMappings)

    val expected = ArrayList<VcsRootError>()
    expected.addAll(unregAll(vcsRootConfiguration.unregErrors))
    expected.addAll(extraAll(vcsRootConfiguration.extraErrors))
    projectRoot.refresh(false, true)
    val actual = ContainerUtil.filter(VcsRootErrorsFinder(myProject).find()
    ) { error -> error.vcsKey == myVcs.keyInstanceMethod }
    VcsTestUtil.assertEqualCollections(actual, expected)
  }

  internal fun addVcsRoots(relativeRoots: Collection<String>) {
    for (root in relativeRoots) {
      if (root == PROJECT) {
        vcsManager.setDirectoryMapping("", myVcsName)
      }
      else {
        val absoluteRoot = VcsTestUtil.toAbsolute(root, myProject)
        vcsManager.setDirectoryMapping(absoluteRoot, myVcsName)
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

  internal fun unreg(path: String): VcsRootError {
    return VcsRootErrorImpl(VcsRootError.Type.UNREGISTERED_ROOT, VcsTestUtil.toAbsolute(path, myProject), myVcsName)
  }

  internal fun extra(path: String): VcsRootError {
    return VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, if (PROJECT == path) PROJECT else VcsTestUtil.toAbsolute(path, myProject),
                            myVcsName)
  }

  companion object {

    internal val PROJECT = VcsDirectoryMapping.PROJECT_CONSTANT
  }
}
