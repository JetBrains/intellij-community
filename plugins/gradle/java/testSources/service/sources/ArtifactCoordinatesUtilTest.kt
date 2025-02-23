// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.sources

import junit.framework.AssertionFailedError
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class ArtifactCoordinatesUtilTest {

  @Test
  fun `test artifact notation parsing`() {
    val dependencyId = getRandomDependencyId()
    val actual = parseArtifactCoordinates(dependencyId.presentableName) { true }
    assertEquals("${dependencyId.group}:${dependencyId.name}:${dependencyId.version}", actual)
  }

  @Test
  fun `test artifact notation parsing with classifier`() {
    val dependencyId = getRandomDependencyId()
    val classifier = getRandomString()
    dependencyId.classifier = classifier

    val actual = parseArtifactCoordinates(dependencyId.presentableName) { true }
    assertEquals("${dependencyId.group}:${dependencyId.name}:${dependencyId.version}", actual)
  }

  @Test
  fun `test artifact notation parsing with packaging`() {
    val dependencyId = getRandomDependencyId()
    val packaging = getRandomString()
    dependencyId.packaging = packaging

    val actual = parseArtifactCoordinates(dependencyId.presentableName) { true }
    assertEquals("${dependencyId.group}:${dependencyId.name}:${dependencyId.version}", actual)
  }

  @Test
  fun `test artifact notation without version`() {
    val group = getRandomString()
    val artifact = getRandomString()

    val actual = parseArtifactCoordinates("$group:$artifact") {
      throw AssertionFailedError("artifactIdChecker shouldn't be called")
    }
    assertEquals("$group:$artifact", actual)
  }


  @Test
  fun `test aar artifact notation parsing`() {
    val group = getRandomString()
    val artifact = getRandomString()
    val version = getRandomString()

    val actual = parseArtifactCoordinates("$group:$artifact:$version@aar") {
      throw AssertionFailedError("artifactIdChecker shouldn't be called")
    }
    assertEquals("$group:$artifact:$version", actual)
  }

  private fun getRandomString(): String {
    return UUID.randomUUID().toString().substring(0, 12)
  }

  private fun getRandomDependencyId(): DefaultExternalDependencyId {
    return DefaultExternalDependencyId(
      getRandomString(),
      getRandomString(),
      getRandomString()
    )
  }
}