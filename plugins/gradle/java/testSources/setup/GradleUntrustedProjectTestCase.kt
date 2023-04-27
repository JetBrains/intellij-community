// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.application.subscribe
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.getResolvedPath
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

abstract class GradleUntrustedProjectTestCase : GradleTestCase() {

  private lateinit var trustedLocations: List<Path>

  @BeforeEach
  fun setUpTrustedLocationsWatcher() {
    val trustedLocations = ArrayList<Path>()
    this.trustedLocations = trustedLocations
    TrustedProjectsListener.TOPIC.subscribe(testDisposable, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) {
        trustedLocations.addAll(locatedProject.projectRoots)
      }
    })
  }

  fun assertTrustedLocations(vararg relativePaths: String) {
    assertTrustedLocations(relativePaths.toList())
  }

  fun assertTrustedLocations(relativePaths: List<String>) {
    Assertions.assertEquals(
      relativePaths.map { testRoot.toNioPath().getResolvedPath(it) }.toSet(),
      trustedLocations.toSet()
    )
  }

  fun assertProjectLocator(
    project: Project,
    vararg relativePaths: String
  ) {
    val locatedProject = TrustedProjectsLocator.locateProject(project)
    Assertions.assertEquals(
      relativePaths.map { testRoot.toNioPath().getResolvedPath(it) }.toSet(),
      locatedProject.projectRoots.toSet()
    )
  }
}