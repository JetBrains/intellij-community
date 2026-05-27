// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.gradle.completion.GradleLocalDependencyCompletionContributor
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ListAssert
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class GradleLocalDependencyCompletionContributorTest {
  @TestDisposable private lateinit var disposable: Disposable

  private val eelDescriptor = LocalEelDescriptor
  private val testProject: Project get() = ProjectManager.getInstance().defaultProject

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "g",
    "group",
    "a",
    "artifact",
    "group:",
    "group:artifact",
    "group:artifact:",
    "group:artifact:version",
  ])
  fun `test search single result`(searchString: String): Unit = runBlocking {
    configureLocalIndex("group:artifact:version")

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version")
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "g",
    "group",
    "a",
    "artifact",
    "group:",
    "group:artifact",
    "group:artifact:",
    "group:artifact:version",
  ])
  fun `test search multiple version results`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version2",
      "group:artifact:version.3",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("group", "artifact", "version2"),
      Triple("group", "artifact", "version.3"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "g",
    "group",
    "a",
    "artifact",
    "group:",
    "group:artifact",
  ])
  fun `test search multiple artifact results`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifactSuffix:version2",
      "group:artifact-suffix:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("group", "artifactSuffix", "version2"),
      Triple("group", "artifact-suffix", "version"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "group:artifact:v",
    "group:artifact:version",
  ])
  fun `test search filter version`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version2",
      "group:artifact:other",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("group", "artifact", "version2"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "a",
    "artifact",
    "group:a",
    "group:artifact",
    "group:artifact:",
    "group:artifact:version",
  ])
  fun `test search filter artifact`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version2",
      "group:other:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("group", "artifact", "version2"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "g",
    "group",
    "group:",
    "group:artifact",
    "group:artifact:",
    "group:artifact:version",
  ])
  fun `test search filter group`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version2",
      "prefix-group:artifact:version",
      "other:artifact:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("group", "artifact", "version2"),
      Triple("prefix-group", "artifact", "version"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "a",
    "artifact",
  ])
  fun `test search on artifact`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version2",
      "group:artifact-suffix:version",
      "other:artifact:version",
      "group:prefix-artifact:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("group", "artifact", "version2"),
      Triple("group", "artifact-suffix", "version"),
      Triple("other", "artifact", "version"),
      Triple("group", "prefix-artifact", "version"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "G",
    "Group",
    "GROUP",
    "A",
    "Artifact",
    "ARTIFACT",
    "Group:",
    "GROUP:",
    "Group:Artifact",
    "GROUP:ARTIFACT",
    "Group:Artifact:",
    "GROUP:ARTIFACT:",
    "Group:Artifact:Version",
    "GROUP:ARTIFACT:VERSION",
  ])
  fun `test search case insensitive`(searchString: String): Unit = runBlocking {
    configureLocalIndex("group:artifact:version")

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version")
    )
  }

  @Test
  fun `test search results order`(): Unit = runBlocking {
    configureLocalIndex(
      "a:b:1",
      "b:b:1",
      "a:c:1",
      "b:c:1",
      "a:b:2",
      "b:b:2",
      "a:c:2",
      "b:c:2",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactly(
      Triple("a", "b", "2"),
      Triple("a", "b", "1"),
      Triple("a", "c", "2"),
      Triple("a", "c", "1"),
      Triple("b", "b", "2"),
      Triple("b", "b", "1"),
      Triple("b", "c", "2"),
      Triple("b", "c", "1"),
    )
  }

  @Test
  fun `test search semantic version order`(): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:0.1",
      "group:artifact:1.0",
      "group:artifact:1.0.1",
      "group:artifact:1.1.0",
      "group:artifact:1.1.0-SNAPSHOT",
      "group:artifact:1.1.1",
      "group:artifact:2.0.0",
      "group:artifact:2.1.0",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactly(
      Triple("group", "artifact", "2.1.0"),
      Triple("group", "artifact", "2.0.0"),
      Triple("group", "artifact", "1.1.1"),
      Triple("group", "artifact", "1.1.0"),
      Triple("group", "artifact", "1.1.0-SNAPSHOT"),
      Triple("group", "artifact", "1.0.1"),
      Triple("group", "artifact", "1.0"),
      Triple("group", "artifact", "0.1"),
    )
  }

  @Test
  fun `test indexer deduplicates dependencies`(): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertEquals(1, results.size)
  }

  @Test
  fun `test indexer deduplicates equivalent semantic versions`(): Unit = runBlocking {
    // only the first gav will be indexed
    configureLocalIndex(
      "group:artifact:1.0.0",
      "group:artifact:1.0",
      "group:artifact:1",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactly(Triple("group", "artifact", "1.0.0"))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "pick-me",
    "pick",
    "me",
  ])
  fun `test search on single part matches on both group and artifact`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:pick-me:version",
      "pick-me:artifact:version",
      "pick-me:pick-me:version",
      "group:artifact:pick-me",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "pick-me", "version"),
      Triple("pick-me", "artifact", "version"),
      Triple("pick-me", "pick-me", "version"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "group:artifact:version",
    "g:a:v",
    "group:artifact:ver"
  ])
  fun `test search uses contains for all parts`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "prefix-groupsuffix:prefixartifact-suffix:prefix.version-suffix",
      "other:artifact:version",
      "group:other:version",
      "group:artifact:other",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      Triple("group", "artifact", "version"),
      Triple("prefix-groupsuffix", "prefixartifact-suffix", "prefix.version-suffix"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "g",
    "grou",
    "group",
  ])
  fun `test group single`(searchString: String): Unit = runBlocking {
    configureLocalIndex("group:artifact:version")

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyGroupCompletionRequest(searchString, "", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getGroups(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder("group")
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "g",
    "grou",
    "group",
  ])
  fun `test group multiple`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:other:version",
      "prefix-group:artifact:version",
      "group-suffix:artifact:version",
      "other:artifact:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyGroupCompletionRequest(searchString, "", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getGroups(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      "group",
      "prefix-group",
      "group-suffix"
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "g",
    "grou",
    "group",
  ])
  fun `test group with artifact filter`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:correct:version",
      "group-wrong:wrong:version",
      "group-also-wrong:correct-not:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyGroupCompletionRequest(searchString, "correct", context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getGroups(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder("group")
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "a",
    "arti",
    "artifact",
  ])
  fun `test artifact single`(searchString: String): Unit = runBlocking {
    configureLocalIndex("group:artifact:version")

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyArtifactCompletionRequest("group", searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getArtifacts(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder("artifact")
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "a",
    "arti",
    "artifact",
  ])
  fun `test artifact multiple`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "other:artifact:version",
      "group:prefix-artifact:version",
      "group:artifact-suffix:version",
      "group:other:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyArtifactCompletionRequest("group", searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getArtifacts(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      "artifact",
      "prefix-artifact",
      "artifact-suffix"
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "a",
    "arti",
    "artifact",
  ])
  fun `test artifact with group filter`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "correct:artifact:version",
      "wrong:artifact-wrong:version",
      "correct-not:artifact-also-wrong:version",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyArtifactCompletionRequest("correct", searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getArtifacts(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder("artifact")
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "v",
    "version",
  ])
  fun `test version single`(searchString: String): Unit = runBlocking {
    configureLocalIndex("group:artifact:version")

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyVersionCompletionRequest("group", "artifact", searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getVersions(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder("version")
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "v",
    "version",
  ])
  fun `test version multiple`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version2",
      "group:artifact:version.3",
      "group:artifact:other",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyVersionCompletionRequest("group", "artifact", searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getVersions(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder(
      "version",
      "version2",
      "version.3",
    )
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "v",
    "version",
  ])
  fun `test version with group and artifact filters`(searchString: String): Unit = runBlocking {
    configureLocalIndex(
      "correct-group:correct-artifact:version",
      "pre-correct-group.post:post.correct-artifact-pre:version1",
      "correct-group:wrong-artifact:version2",
      "wrong-group:correct-artifact:version.3",
    )

    val context = GradleDependencyCompletionContext(testProject)
    val request = DependencyVersionCompletionRequest("correct-group", "correct-artifact", searchString, context)
    val contributor = GradleLocalDependencyCompletionContributor()
    val results = contributor.getVersions(request)

    assertThat(results).containsLocalDependenciesExactlyInAnyOrder("version")
  }

  private fun configureLocalIndex(vararg gav: String) {
    application.replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(eelDescriptor, *gav),
      disposable
    )
  }

  private fun ListAssert<DependencyCompletionResult>.containsLocalDependenciesExactlyInAnyOrder(
    vararg expected: Triple<String, String, String>,
  ): ListAssert<DependencyCompletionResult> {
    val expectedResults = expected.map { (groupId, artifactId, version) ->
      DependencyCompletionResult(groupId, artifactId, version, source = DependencyCompletionContributionSource.LOCAL)
    }.toTypedArray()
    return containsExactlyInAnyOrder(*expectedResults)
  }

  private fun ListAssert<DependencyCompletionResult>.containsLocalDependenciesExactly(
    vararg expected: Triple<String, String, String>,
  ): ListAssert<DependencyCompletionResult> {
    val expectedResults = expected.map { (groupId, artifactId, version) ->
      DependencyCompletionResult(groupId, artifactId, version, source = DependencyCompletionContributionSource.LOCAL)
    }.toTypedArray()
    return containsExactly(*expectedResults)
  }

  private fun ListAssert<DependencyPartCompletionResult>.containsLocalDependenciesExactlyInAnyOrder(
    vararg expected: String,
  ): ListAssert<DependencyPartCompletionResult> {
    val expectedResults = expected.map { part ->
      DependencyPartCompletionResult(part, source = DependencyCompletionContributionSource.LOCAL)
    }.toTypedArray()
    return containsExactlyInAnyOrder(*expectedResults)
  }
}

private class GradleDependencyCompletionContext(override val project: Project) : DependencyCompletionContext {
  override val buildSystemId: ProjectSystemId
    get() = GradleConstants.SYSTEM_ID
}
