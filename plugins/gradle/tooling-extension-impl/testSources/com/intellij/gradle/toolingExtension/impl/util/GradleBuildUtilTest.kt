// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertSingle
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource

@TestFixtures
@ParameterizedClass
@ValueSource(strings = ["7.6.6", "8.0"])
class GradleBuildUtilTest(gradleVersionValue: String) {

  private val gradleVersion: GradleVersion = GradleVersion.version(gradleVersionValue)

  private val testRoot by tempPathFixture()

  @Nested
  inner class IsValidBuildModel {

    @Test
    fun `test returns true for valid build model`() {
      val build = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )

      assertTrue(GradleBuildUtil.isValidBuildModel(build))
    }

    @Test
    fun `test returns false for build model without build identifier`() {
      val buildWithoutIdentifier = mockGradleBuild(
        buildIdentifier = null,
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )

      assertFalse(GradleBuildUtil.isValidBuildModel(buildWithoutIdentifier))
    }

    @Test
    fun `test returns false for build model without root project`() {
      val buildWithoutRootProject = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = null,
        nestedBuilds = emptyList()
      )

      assertFalse(GradleBuildUtil.isValidBuildModel(buildWithoutRootProject))
    }
  }

  @Nested
  inner class GetAllNestedBuilds {

    @Test
    fun `test returns all nested build models`() {
      val nestedNestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/nested/nestedNested"),
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )
      val nestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/nested"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(nestedNestedBuild)
      )
      val rootBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(nestedBuild)
      )

      val nestedBuilds = GradleBuildUtil.getAllNestedBuilds(rootBuild, gradleVersion)

      assertEqualsOrdered(listOf(nestedBuild, nestedNestedBuild), nestedBuilds)
    }

    @Test
    fun `test returns empty collection when build has no nested builds`() {
      val rootBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )

      val nestedBuilds = GradleBuildUtil.getAllNestedBuilds(rootBuild, gradleVersion)

      assertEmpty(nestedBuilds)
    }

    @Test
    fun `test excludes invalid nested build models`() {
      val nestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/nested"),
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )
      val nestedBuildWithoutIdentifier = mockGradleBuild(
        buildIdentifier = null,
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )
      val nestedBuildWithoutRootProject = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/corrupted"),
        rootProject = null,
        nestedBuilds = emptyList()
      )
      val rootBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(nestedBuild, nestedBuildWithoutIdentifier, nestedBuildWithoutRootProject)
      )

      val nestedBuilds = GradleBuildUtil.getAllNestedBuilds(rootBuild, gradleVersion)

      assertSingle(nestedBuild, nestedBuilds)
    }

    @Test
    // https://docs.gradle.org/6.8.3/release-notes.html#desired-cycles-between-builds-are-now-fully-supported
    fun `test excludes already visited root build from cyclic composites`() {
      val nestedNestedBuilds = ArrayList<GradleBuild>()
      val nestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/nested"),
        rootProject = mockGradleProject(),
        nestedBuilds = nestedNestedBuilds
      )
      val rootBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(nestedBuild)
      )
      nestedNestedBuilds.add(rootBuild)

      val nestedBuilds = GradleBuildUtil.getAllNestedBuilds(rootBuild, gradleVersion)

      assertSingle(nestedBuild, nestedBuilds)
    }

    @Test
    // https://docs.gradle.org/6.8.3/release-notes.html#desired-cycles-between-builds-are-now-fully-supported
    fun `test excludes already visited nested builds from cyclic composites`() {
      val nestedNestedBuildNestedBuilds = ArrayList<GradleBuild>()
      val nestedNestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/nested/nestedNested"),
        rootProject = mockGradleProject(),
        nestedBuilds = nestedNestedBuildNestedBuilds
      )
      val nestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/nested"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(nestedNestedBuild)
      )
      val rootBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(nestedBuild)
      )
      nestedNestedBuildNestedBuilds.add(nestedBuild)

      val nestedBuilds = GradleBuildUtil.getAllNestedBuilds(rootBuild, gradleVersion)

      assertEqualsOrdered(listOf(nestedBuild, nestedNestedBuild), nestedBuilds)
    }

    @Test
    fun `test excludes duplicated nested builds from shared build logic`() {
      val buildLogicBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/build-logic"),
        rootProject = mockGradleProject(),
        nestedBuilds = emptyList()
      )
      val firstNestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/first"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(buildLogicBuild)
      )
      val secondNestedBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root/second"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(buildLogicBuild)
      )
      val rootBuild = mockGradleBuild(
        buildIdentifier = mockBuildIdentifier("root"),
        rootProject = mockGradleProject(),
        nestedBuilds = listOf(firstNestedBuild, secondNestedBuild)
      )

      val nestedBuilds = GradleBuildUtil.getAllNestedBuilds(rootBuild, gradleVersion)

      assertEqualsOrdered(listOf(firstNestedBuild, secondNestedBuild, buildLogicBuild), nestedBuilds)
    }
  }

  @Suppress("IO_FILE_USAGE")
  private fun mockBuildIdentifier(relativePath: String): BuildIdentifier =
    DefaultBuildIdentifier(testRoot.resolve(relativePath).toFile())

  private fun mockGradleProject(): BasicGradleProject =
    object : BasicGradleProject by notImplemented(BasicGradleProject::class.java) {}

  private fun mockGradleBuild(
    buildIdentifier: BuildIdentifier?,
    rootProject: BasicGradleProject?,
    nestedBuilds: Collection<GradleBuild>,
  ): GradleBuild = object : GradleBuild by notImplemented(GradleBuild::class.java) {

    override fun getBuildIdentifier(): BuildIdentifier? = buildIdentifier

    override fun getRootProject() = rootProject

    override fun getIncludedBuilds(): ImmutableDomainObjectSet<GradleBuild> {
      return when (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")) {
        true -> ImmutableDomainObjectSet.of(nestedBuilds)
        else -> ImmutableDomainObjectSet.of(emptyList())
      }
    }

    override fun getEditableBuilds(): ImmutableDomainObjectSet<GradleBuild> {
      assertFalse(GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")) {
        "GradleBuild#getEditableBuilds shouldn't be called when it isn't supported."
      }
      return ImmutableDomainObjectSet.of(nestedBuilds)
    }

    override fun toString(): String = buildIdentifier?.rootDir?.path ?: "<no-build-identifier>"
  }
}
