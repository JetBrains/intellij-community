// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.common.BazelTestUtil
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.extractZip
import org.jetbrains.intellij.bazelEnvironment.BazelLabel
import java.nio.file.Path

data class BazelLabelArchiveProjectInfo(
  val bazelLabel: String,
  val projectHomeRelativePath: (Path) -> Path = { it },
  val testDependencyLoader: BazelTestDependencyHttpFileDownloader,
) {
  fun unpackProject(): Path {

    val label = BazelLabel.fromString(bazelLabel)
    val projectZipFile = testDependencyLoader.getDepsByLabel(label)

    val projectsUnpacked = if (BazelTestUtil.isUnderBazelTest) {
      val target = BazelTestUtil.bazelTestTmpDirPath.resolve(label.target)
      extractZip(projectZipFile, target, false)
      target
    } else {
      extractFileToCacheLocation(communityRoot = BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath())), projectZipFile)
    }

    val projectHome = projectsUnpacked.let(projectHomeRelativePath)
    return projectHome
  }
}
