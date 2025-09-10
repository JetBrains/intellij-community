// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil.isBuildSrcAddedInEditableBuilds
import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil.isBuildSrcSyncedSeparately
import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil.isBuildTreePathAvailable
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.BASE_GRADLE_VERSION
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalProjectIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DefaultGradleLightBuildTest {

  @ParameterizedTest
  @GradleTestSource("8.1, 8.2")
  fun `test DefaultGradleLightBuild#convertGradleBuilds for simple build`(gradleVersion: GradleVersion) {
    // GIVEN
    val rootProject = mockGradleProject(
      path = ":", buildTreePath = ":",
      buildPath = "/rootBuild",
      gradleVersion = gradleVersion,
    )
    val subproject = mockGradleProject(
      path = ":subproject", buildTreePath = ":subproject",
      buildPath = "/rootBuild",
      projectPath = "/rootBuild/subproject",
      gradleVersion = gradleVersion,
      parent = rootProject
    )
    val gradleRootBuild = mockGradleBuild(
      buildPath = "/rootBuild",
      buildProjects = listOf(rootProject, subproject)
    )

    // WHEN
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(listOf(gradleRootBuild), gradleVersion)

    // THEN
    assertEquals(1, convertedBuilds.size)
    val rootBuild = convertedBuilds.single()

    verifyBuild(
      build = rootBuild,
      buildPath = "/rootBuild",
      projectNames = listOf("rootBuild", "subproject"))
    verifyProject(
      project = rootBuild.projects[0],
      projectPath = "/rootBuild",
      identityPath = ":",
      build = rootBuild, path = ":",
      subprojects = listOf(rootBuild.projects[1]),
    )
    verifyProject(
      project = rootBuild.projects[1],
      identityPath = ":subproject",
      build = rootBuild, path = ":subproject",
      projectPath = "/rootBuild/subproject",
    )
  }

  @ParameterizedTest
  @GradleTestSource("8.1, 8.2")
  fun `test DefaultGradleLightBuild#convertGradleBuilds for composite build`(gradleVersion: GradleVersion) {
    // GIVEN
    val rootProject = mockGradleProject(path = ":", buildTreePath = ":", buildPath = "/rootBuild", gradleVersion = gradleVersion)
    val gradleRootBuild = mockGradleBuild(buildPath = "/rootBuild", buildProjects = listOf(rootProject))

    val includedRootProject = mockGradleProject(
      path = ":", buildTreePath = ":includedBuild",
      buildPath = "/rootBuild/includedBuild",
      gradleVersion = gradleVersion,
    )
    val includedSubproject = mockGradleProject(
      path = ":subproject", buildTreePath = ":includedBuild:subproject",
      buildPath = "/rootBuild/includedBuild",
      projectPath = "/rootBuild/includedBuild/subproject",
      gradleVersion = gradleVersion,
      parent = includedRootProject
    )
    val gradleIncludedBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild",
      buildProjects = listOf(includedRootProject, includedSubproject)
    )

    val deepIncludedRootProject = mockGradleProject(
      path = ":", buildTreePath = ":includedBuild:deepIncludedBuild",
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      gradleVersion = gradleVersion,
    )
    val deepIncludedSubproject = mockGradleProject(
      path = ":subproject", buildTreePath = ":includedBuild:deepIncludedBuild:subproject",
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      projectPath = "/rootBuild/includedBuild/deepIncludedBuild/subproject",
      gradleVersion = gradleVersion,
      parent = deepIncludedRootProject
    )
    val gradleDeepIncludedBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      buildProjects = listOf(deepIncludedRootProject, deepIncludedSubproject)
    )

    // Set hierarchy
    gradleRootBuild.mockIncludedBuilds(gradleIncludedBuild)
    // Editable builds of the root contain all included builds transitively.
    // However, the converted deep-included build should have a correct parent, not the root build.
    gradleRootBuild.mockEditableBuildsForRoot(gradleIncludedBuild, gradleDeepIncludedBuild)
    gradleIncludedBuild.mockIncludedBuilds(gradleDeepIncludedBuild)

    // WHEN
    val gradleBuilds = listOf(gradleRootBuild, gradleIncludedBuild, gradleDeepIncludedBuild)
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(gradleBuilds, gradleVersion)

    // THEN
    assertEquals(3, convertedBuilds.size) { "The number of expected builds should match the expected value" }
    val (rootBuild, includedBuild, deepIncludedBuild) = convertedBuilds

    verifyBuild(rootBuild, "/rootBuild", listOf("rootBuild"))

    verifyBuild(
      build = includedBuild,
      buildPath = "/rootBuild/includedBuild",
      projectNames = listOf("includedBuild", "subproject"),
      parent = rootBuild,
    )
    verifyProject(
      project = includedBuild.projects[0],
      projectPath = "/rootBuild/includedBuild",
      identityPath = ":includedBuild",
      build = includedBuild, path = ":",
      subprojects = listOf(includedBuild.projects[1]),
    )
    verifyProject(
      project = includedBuild.projects[1],
      projectPath = "/rootBuild/includedBuild/subproject",
      identityPath = ":includedBuild:subproject",
      build = includedBuild, path = ":subproject",
    )

    verifyBuild(
      build = deepIncludedBuild,
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      projectNames = listOf("deepIncludedBuild", "subproject"),
      parent = includedBuild, // it shouldn't have a root build as a parent!
    )
    verifyProject(
      project = deepIncludedBuild.projects[0],
      projectPath = "/rootBuild/includedBuild/deepIncludedBuild",
      identityPath = ":includedBuild:deepIncludedBuild",
      build = deepIncludedBuild, path = ":",
      subprojects = listOf(deepIncludedBuild.projects[1]),
    )
    verifyProject(
      project = deepIncludedBuild.projects[1],
      projectPath = "/rootBuild/includedBuild/deepIncludedBuild/subproject",
      identityPath = ":includedBuild:deepIncludedBuild:subproject",
      build = deepIncludedBuild, path = ":subproject",
    )
  }

  /**
   * Gradle >= 8.0: for buildSrc, IDEA sets a parent build corresponding to the parent directory of the buildSrc.
   *```
   * rootBuild/
   * ├── buildSrc/
   * └── includedBuild/
   *     └── buildSrc/
   *```
   * It's worth running this test for 8.0, because before 8.2 identity path calculation for a project depends on the build hierarchy.
   */
  @ParameterizedTest
  @GradleTestSource("8.0, $BASE_GRADLE_VERSION")
  fun `test converted buildSrc has a parent build since Gradle 8,0`(gradleVersion: GradleVersion) {
    val gradleRootProject = mockGradleProject(
      buildPath = "/rootBuild",
      path = ":", buildTreePath = ":",
      gradleVersion = gradleVersion
    )
    val gradleRootBuild = mockGradleBuild(
      buildPath = "/rootBuild",
      buildProjects = listOf(gradleRootProject)
    )

    val gradleBuildSrcProject = mockGradleProject(
      buildPath = "/rootBuild/buildSrc",
      path = ":", buildTreePath = ":buildSrc",
      gradleVersion = gradleVersion
    )
    val gradleBuildSrcBuild = mockGradleBuild(
      buildPath = "/rootBuild/buildSrc",
      buildProjects = listOf(gradleBuildSrcProject),
    )

    val gradleIncludedProject = mockGradleProject(
      buildPath = "/rootBuild/includedBuild",
      path = ":", buildTreePath = ":includedBuild",
      gradleVersion = gradleVersion
    )
    val gradleIncludedBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild",
      buildProjects = listOf(gradleIncludedProject),
    )

    val gradleIncludedBuildSrcProject = mockGradleProject(
      buildPath = "/rootBuild/includedBuild/buildSrc",
      path = ":", buildTreePath = ":includedBuild:buildSrc",
      gradleVersion = gradleVersion
    )
    val gradleIncludedBuildSrcBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild/buildSrc",
      buildProjects = listOf(gradleIncludedBuildSrcProject),
    )

    // Set hierarchy
    gradleRootBuild.mockIncludedBuilds(gradleIncludedBuild)
    assertTrue(isBuildSrcAddedInEditableBuilds(gradleVersion)) { "This test is supposed to be executed for Gradle higher than 7.2" }
    // Gradle 7.2+ puts in editableBuilds all buildSrc belonging to all transitively included builds of the root build.
    gradleRootBuild.mockEditableBuildsForRoot(gradleBuildSrcBuild, gradleIncludedBuild, gradleIncludedBuildSrcBuild)

    // WHEN
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(
      listOf(gradleRootBuild, gradleBuildSrcBuild, gradleIncludedBuild, gradleIncludedBuildSrcBuild), gradleVersion
    )

    // THEN
    assertEquals(4, convertedBuilds.size) { "The number of expected builds should match the expected value" }
    val (rootBuild, buildSrcBuild, includedBuild, includedBuildSrcBuild) = convertedBuilds

    verifyBuild(
      build = buildSrcBuild,
      buildPath = "/rootBuild/buildSrc",
      projectNames = listOf("buildSrc"),
      parent = rootBuild // the main assertion for this test
    )
    verifyProject(
      project = buildSrcBuild.rootProject,
      projectPath = "/rootBuild/buildSrc",
      identityPath = ":buildSrc",
      build = buildSrcBuild, path = ":",
    )

    verifyBuild(
      build = includedBuildSrcBuild,
      buildPath = "/rootBuild/includedBuild/buildSrc",
      projectNames = listOf("buildSrc"),
      parent = includedBuild // the main assertion for this test
    )
    verifyProject(
      project = includedBuildSrcBuild.rootProject,
      projectPath = "/rootBuild/includedBuild/buildSrc",
      identityPath = ":includedBuild:buildSrc", // until 8.2 an identity path is calculated relying on the build hierarchy
      build = includedBuildSrcBuild, path = ":",
    )
  }

  /**
   * Gradle < 8.0: IDEA currently is not able to set a parent for buildSrc because it is synced separately until 8.0.
   * This is not the desired behavior, it causes problems with identity path calculation. This test just declares it.
   *
   * Also, from 6.7-rc-1 until 8.0, buildSrc has access to included builds of the build it belongs to.
   * See [release notes for 6.7-rc-1](https://docs.gradle.org/6.7-rc-1/release-notes.html#build-src).
   * To follow this behavior, when IDEA syncs buildSrc, it attaches included builds of the parent build for buildSrc.
   * See [org.jetbrains.plugins.gradle.service.project.GradleBuildSrcProjectsResolver.includeRootBuildIncludedBuildsIfNeeded]
   *```
   * rootBuild/
   * ├── buildSrc/
   * └── includedBuild/
   *```
   */
  @ParameterizedTest
  @GradleTestSource("6.6, 7.6")
  fun `test converted buildSrc does not have a parent before Gradle 8,0`(gradleVersion: GradleVersion) {
    val gradleRootProject = mockGradleProject(
      buildPath = "/rootBuild",
      path = ":", buildTreePath = ":",
      gradleVersion = gradleVersion
    )
    val gradleRootBuild = mockGradleBuild(
      buildPath = "/rootBuild",
      buildProjects = listOf(gradleRootProject)
    )

    val gradleBuildSrcProject = mockGradleProject(
      buildPath = "/rootBuild/buildSrc",
      path = ":", buildTreePath = ":buildSrc",
      gradleVersion = gradleVersion
    )
    val gradleBuildSrcBuild = mockGradleBuild(
      buildPath = "/rootBuild/buildSrc",
      buildProjects = listOf(gradleBuildSrcProject),
    )

    val gradleIncludedProject = mockGradleProject(
      buildPath = "/rootBuild/includedBuild",
      path = ":", buildTreePath = ":includedBuild",
      gradleVersion = gradleVersion
    )
    val gradleIncludedBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild",
      buildProjects = listOf(gradleIncludedProject),
    )

    val gradleIncludedBuildSrcProject = mockGradleProject(
      buildPath = "/rootBuild/includedBuild/buildSrc",
      path = ":", buildTreePath = ":includedBuild:buildSrc",
      gradleVersion = gradleVersion
    )
    val gradleIncludedBuildSrcBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild/buildSrc",
      buildProjects = listOf(gradleIncludedBuildSrcProject),
    )

    // Set hierarchy
    gradleRootBuild.mockIncludedBuilds(gradleIncludedBuild)
    val editableBuilds = if (isBuildSrcAddedInEditableBuilds(gradleVersion)) {
      // Gradle 7.2+ puts in editableBuilds all buildSrc belonging to all transitively included builds of the root build.
      arrayOf(gradleBuildSrcBuild, gradleIncludedBuild, gradleIncludedBuildSrcBuild)
    } else {
      arrayOf(gradleIncludedBuildSrcBuild)
    }
    gradleRootBuild.mockEditableBuildsForRoot(*editableBuilds)

    // The root's included build is attached to buildSrc because IDEA includes it when syncs buildSrc separately
    gradleBuildSrcBuild.mockIncludedBuilds(gradleIncludedBuild)

    // WHEN
    assertTrue(isBuildSrcSyncedSeparately(gradleVersion)) { "This test is supposed to be executed for Gradle below 8.0" }
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(
      listOf(gradleRootBuild, gradleIncludedBuild),
      gradleVersion
    ) + DefaultGradleLightBuild.convertGradleBuilds(
      // when IDEA syncs buildSrc, it attaches included builds of the parent build for buildSrc.
      listOf(gradleBuildSrcBuild, gradleIncludedBuild),
      gradleVersion
    )

    // THEN
    assertEquals(4, convertedBuilds.size) { "The number of expected builds should match the expected value" }
    val (rootBuild, includedBuild, buildSrcBuild, includedBuildVisibleToBuildSrc) = convertedBuilds

    verifyBuild(
      build = includedBuild,
      buildPath = "/rootBuild/includedBuild",
      projectNames = listOf("includedBuild"),
      parent = rootBuild
    )
    verifyProject(
      project = includedBuild.rootProject,
      projectPath = "/rootBuild/includedBuild",
      identityPath = ":includedBuild",
      build = includedBuild, path = ":",
    )

    verifyBuild(
      build = buildSrcBuild,
      buildPath = "/rootBuild/buildSrc",
      projectNames = listOf("buildSrc"),
      parent = null
    )
    verifyProject(
      project = buildSrcBuild.rootProject,
      projectPath = "/rootBuild/buildSrc",
      // A correct identityPath would be `:buildSrc`. It has this value instead because buildSrc is synced separately until 8.0.
      // However, Gradle provides the same in GradleProjectUtil.getProjectIdentityPath.
      identityPath = ":",
      build = buildSrcBuild, path = ":",
    )

    // This included build belongs to the rootBuild. But in the standalone sync for buildSrc it comes as an included build of buildSrc.
    verifyBuild(
      build = includedBuildVisibleToBuildSrc,
      buildPath = "/rootBuild/includedBuild",
      projectNames = listOf("includedBuild"),
      parent = buildSrcBuild
    )
    verifyProject(
      project = includedBuildVisibleToBuildSrc.rootProject,
      projectPath = "/rootBuild/includedBuild",
      // In this case, the identityPath seems correct, the same as when the included build is attached to the root build.
      // That's because in this case buildSrc is synced separately for this sync it is like a "root" build.
      identityPath = ":includedBuild",
      build = includedBuildVisibleToBuildSrc, path = ":",
    )
  }

  private fun mockGradleProject(
    path: String,
    buildTreePath: String,
    buildPath: String,
    projectPath: String = buildPath,
    gradleVersion: GradleVersion,
    parent: BasicGradleProject? = null,
  ): BasicGradleProject = mock {
    val buildIdentifier = InternalBuildIdentifier(File(buildPath))
    on { this.projectIdentifier } doReturn InternalProjectIdentifier(buildIdentifier, path)
    on { this.name } doReturn File(projectPath).name
    on { this.path } doReturn path
    on { this.projectDirectory } doReturn File(projectPath)
    on { this.parent } doReturn parent
    on { this.children } doReturn ImmutableDomainObjectSet(emptyList())

    if (isBuildTreePathAvailable(gradleVersion)) {
      assertNotNull(buildTreePath) { "Please define buildTreePath for tests with Gradle 8.2 or newer." }
      on { this.buildTreePath } doReturn buildTreePath
    }
    else {
      on { this.buildTreePath } doThrow IllegalStateException(
        "getBuildTreePath() is not available in Gradle below 8.2, so shouldn't be called."
      )
    }

    if (parent != null) {
      val subprojects = parent.children + it
      on { parent.children } doReturn ImmutableDomainObjectSet(subprojects)
    }
  }

  private fun mockGradleBuild(
    buildPath: String,
    buildProjects: List<BasicGradleProject>,
  ): GradleBuild = mock {
    on { buildIdentifier } doReturn DefaultBuildIdentifier(File(buildPath))
    on { rootProject } doReturn buildProjects.first()
    on { projects } doReturn ImmutableDomainObjectSet(buildProjects)
    on { includedBuilds } doReturn ImmutableDomainObjectSet(emptyList())
    on { editableBuilds } doReturn ImmutableDomainObjectSet(emptyList())
  }

  /**
   * Only the root build could have editable builds. Those are included builds of it and all their included builds transitively.
   * Since 7.2, it includes buildSrc of the root build and buildSrc of all its transitive included builds.
   * `editableBuilds` doesn't contain included builds of a buildSrc belonging to a root or any included build.
   *
   * [Gradle documentation for `getEditableBuilds`](https://docs.gradle.org/current/javadoc/org/gradle/tooling/model/gradle/GradleBuild.html#getEditableBuilds())
   */
  private fun GradleBuild.mockEditableBuildsForRoot(
    vararg editableBuildsMocks: GradleBuild,
  ) {
    whenever(this.editableBuilds) doReturn ImmutableDomainObjectSet(editableBuildsMocks.asList())
  }

  /** Use it to specify included builds only. For buildSrc, please use [mockEditableBuildsForRoot] */
  private fun GradleBuild.mockIncludedBuilds(
    vararg includedBuildsMocks: GradleBuild,
  ) {
    assertTrue(includedBuildsMocks.asList().none { it.isBuildSrcBuild() }) {
      "`buildSrc` shouldn't be added in included builds. Gradle puts all buildSrc only in editable builds and only for the root build."
    }
    whenever(this.includedBuilds) doReturn ImmutableDomainObjectSet(includedBuildsMocks.asList())
  }

  private fun GradleBuild.isBuildSrcBuild(): Boolean {
    val buildDirectory = this.buildIdentifier.rootDir
    return buildDirectory.name.equals("buildSrc", ignoreCase = true)
  }

  private fun verifyBuild(
    build: DefaultGradleLightBuild,
    buildPath: String,
    projectNames: List<String>,
    parent: DefaultGradleLightBuild? = null,
  ) {
    assertEquals(File(buildPath).name, build.name) {
      "Build name should be equal to its root project name: by default, it's the name of directory where settings.gradle(.kts) is located."
    }
    assertEquals(buildPath, build.buildIdentifier.rootDir.path) {
      "The build directory specified in the build identifier should match the given path."
    }
    assertEquals(parent, build.parentBuild) {
      "The build should have an expected `parentBuild`. The opposite indicates that the build hierarchy has been broken."
    }
    assertEquals(projectNames, build.projects.map { it.name }) {
      "The build should have projects with expected names."
    }
  }

  private fun verifyProject(
    project: DefaultGradleLightProject,
    projectPath: String,
    identityPath: String,
    build: DefaultGradleLightBuild,
    path: String,
    subprojects: List<DefaultGradleLightProject>? = emptyList(),
  ) {
    assertEquals(build, project.build) {
      "The project should belong to the given `build`."
    }
    assertEquals(File(projectPath).name, project.name) {
      "The project should have expected `name`. By default, it is the name of its directory."
    }
    assertEquals(path, project.path) {
      "The project should have expected `path`. This path is relative to project's build and separated with `:`."
    }
    assertEquals(identityPath, project.identityPath) {
      "The project should have expected `identityPath`. It identifies the project, relatively to the settings file of the root build. " +
      "Before 8.2 it is calculated relying on the known hierarchy of builds and projects. After 8.2, it is taken from `buildTreePath`."
    }
    assertEquals(projectPath, project.projectDirectory.path) {
      "The project should be located in expected `projectDirectory`"
    }
    assertEquals(build.buildIdentifier.rootDir, project.projectIdentifier.buildIdentifier.rootDir) {
      "The `projectIdentifier` is based on the `buildIdentifier` of its build, which should have specified the expected build directory."
    }
    assertEquals(path, project.projectIdentifier.projectPath) {
      "The `projectIdentifier` should have expected `projectPath`. This path is relative to project's build and separated with `:`."
    }
    assertEquals(subprojects, project.childProjects) {
      "The project should have expected `subprojects`."
    }
  }
}