// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.openapi.Disposable
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.completion.api.*
import org.jetbrains.plugins.gradle.service.cache.GradleLocalRepositoryIndexer
import org.jetbrains.plugins.gradle.service.cache.GradleLocalRepositoryIndexerTestImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class GradleDependencyCompletionContributorTest {
  @TestDisposable private lateinit var disposable: Disposable

  private val eelDescriptor = LocalEelDescriptor

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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version")
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("group", "artifact", "version2"),
      DependencyCompletionResult("group", "artifact", "version.3"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("group", "artifactSuffix", "version2"),
      DependencyCompletionResult("group", "artifact-suffix", "version"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("group", "artifact", "version2"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("group", "artifact", "version2"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("group", "artifact", "version2"),
      DependencyCompletionResult("prefix-group", "artifact", "version"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("group", "artifact", "version2"),
      DependencyCompletionResult("group", "artifact-suffix", "version"),
      DependencyCompletionResult("other", "artifact", "version"),
      DependencyCompletionResult("group", "prefix-artifact", "version"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version")
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactly(
      DependencyCompletionResult("a", "b", "2"),
      DependencyCompletionResult("a", "b", "1"),
      DependencyCompletionResult("a", "c", "2"),
      DependencyCompletionResult("a", "c", "1"),
      DependencyCompletionResult("b", "b", "2"),
      DependencyCompletionResult("b", "b", "1"),
      DependencyCompletionResult("b", "c", "2"),
      DependencyCompletionResult("b", "c", "1"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactly(
      DependencyCompletionResult("group", "artifact", "2.1.0"),
      DependencyCompletionResult("group", "artifact", "2.0.0"),
      DependencyCompletionResult("group", "artifact", "1.1.1"),
      DependencyCompletionResult("group", "artifact", "1.1.0"),
      DependencyCompletionResult("group", "artifact", "1.1.0-SNAPSHOT"),
      DependencyCompletionResult("group", "artifact", "1.0.1"),
      DependencyCompletionResult("group", "artifact", "1.0"),
      DependencyCompletionResult("group", "artifact", "0.1"),
    )
  }

  @Test
  fun `test indexer deduplicates dependencies`(): Unit = runBlocking {
    configureLocalIndex(
      "group:artifact:version",
      "group:artifact:version",
    )

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleDependencyCompletionContributor()
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest("", context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactly(DependencyCompletionResult("group", "artifact", "1.0.0"))
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "pick-me", "version"),
      DependencyCompletionResult("pick-me", "artifact", "version"),
      DependencyCompletionResult("pick-me", "pick-me", "version"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyCompletionRequest(searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.search(request)

    assertThat(results).containsExactlyInAnyOrder(
      DependencyCompletionResult("group", "artifact", "version"),
      DependencyCompletionResult("prefix-groupsuffix", "prefixartifact-suffix", "prefix.version-suffix"),
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyGroupCompletionRequest(searchString, "", context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getGroups(request)

    assertThat(results).containsExactlyInAnyOrder("group")
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyGroupCompletionRequest(searchString, "", context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getGroups(request)

    assertThat(results).containsExactlyInAnyOrder(
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyGroupCompletionRequest(searchString, "correct", context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getGroups(request)

    assertThat(results).containsExactlyInAnyOrder("group")
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyArtifactCompletionRequest("group", searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getArtifacts(request)

    assertThat(results).containsExactlyInAnyOrder("artifact")
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyArtifactCompletionRequest("group", searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getArtifacts(request)

    assertThat(results).containsExactlyInAnyOrder(
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyArtifactCompletionRequest("correct", searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getArtifacts(request)

    assertThat(results).containsExactlyInAnyOrder("artifact")
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "",
    "v",
    "version",
  ])
  fun `test version single`(searchString: String): Unit = runBlocking {
    configureLocalIndex("group:artifact:version")

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyVersionCompletionRequest("group", "artifact", searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getVersions(request)

    assertThat(results).containsExactlyInAnyOrder("version")
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyVersionCompletionRequest("group", "artifact", searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getVersions(request)

    assertThat(results).containsExactlyInAnyOrder(
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

    val context = GradleDependencyCompletionContext(eelDescriptor)
    val request = DependencyVersionCompletionRequest("correct-group", "correct-artifact", searchString, context)
    val contributor = GradleDependencyCompletionContributor()
    val results = contributor.getVersions(request)

    assertThat(results).containsExactlyInAnyOrder("version")
  }

  private fun configureLocalIndex(vararg gav: String) {
    application.replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(eelDescriptor, *gav),
      disposable
    )
  }
}
