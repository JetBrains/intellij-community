// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.deleteTestDir
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractZip
import java.nio.file.Path

data class BazelLabelArchiveProjectInfo(
  val bazelLabel: String,
  val projectHomeRelativePath: (Path) -> Path = { it },
  val testDependencyLoader: BazelTestDependencyHttpFileDownloader,
) {
  /**
   * Unpacks the archive and returns the project home. Under `bazel test` it extracts into `TEST_TMPDIR`, which only
   * grows: prefer [withUnpackedProject], which frees it.
   */
  fun unpackProject(): Path {
    val label = BazelLabel.fromString(bazelLabel)
    val projectZipFile = testDependencyLoader.getDepsByLabel(label)

    val projectsUnpacked = if (BazelTestUtil.isUnderBazelTest) {
      val target = bazelTestExtractionTarget()
      extractZip(projectZipFile, target, false)
      target
    }
    else {
      extractFileToCacheLocation(communityRoot = BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath())), projectZipFile)
    }

    val projectHome = projectsUnpacked.let(projectHomeRelativePath)
    return projectHome
  }

  /**
   * Unpacks the archive, runs [action] on the project home, then deletes the extraction under `bazel test`.
   * Outside Bazel the extraction is the shared download cache, so it stays.
   * The extraction path is the same for each call, so a shared index of the project stays valid.
   */
  inline fun <T> withUnpackedProject(action: (Path) -> T): T {
    val projectHome = unpackProject()
    try {
      return action(projectHome)
    }
    finally {
      deleteUnpackedProject()
    }
  }

  @PublishedApi
  internal fun deleteUnpackedProject() {
    if (BazelTestUtil.isUnderBazelTest) {
      deleteTestDir(bazelTestExtractionTarget())
    }
  }

  private fun bazelTestExtractionTarget(): Path =
    BazelTestUtil.bazelTestTmpDirPath.resolve(BazelLabel.fromString(bazelLabel).target)
}
