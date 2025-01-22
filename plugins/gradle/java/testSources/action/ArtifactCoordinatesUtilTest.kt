// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import junit.framework.AssertionFailedError
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.jetbrains.plugins.gradle.service.sources.parseArtifactCoordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactCoordinatesUtilTest {

  @Test
  fun `test artifact notation parsing`() {
    val dependencyId = DefaultExternalDependencyId("mygroup", "myartifact", "myversion")
    assertEquals("mygroup:myartifact:myversion",
                 parseArtifactCoordinates(dependencyId.presentableName) {
                   true
                 })

    dependencyId.classifier = "myclassifier"
    assertEquals("mygroup:myartifact:myversion",
                 parseArtifactCoordinates(dependencyId.presentableName) {
                   true
                 })

    dependencyId.packaging = "mypackaging"
    assertEquals("mygroup:myartifact:myversion",
                 parseArtifactCoordinates(dependencyId.presentableName) {
                   true
                 })

    assertEquals("mygroup:myartifact:myversion",
                 parseArtifactCoordinates(
                   DefaultExternalDependencyId("mygroup", "myartifact", "myversion").apply { packaging = "mypackaging" }.presentableName) {
                   true
                 })

    assertEquals("myartifact:myversion",
                 parseArtifactCoordinates("myartifact:myversion") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact",
                 parseArtifactCoordinates("mygroup:myartifact") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:myversion",
                 parseArtifactCoordinates("mygroup:myartifact:myversion@aar") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })
  }
}
