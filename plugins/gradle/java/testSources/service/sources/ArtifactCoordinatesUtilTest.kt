// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.sources

import junit.framework.AssertionFailedError
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.junit.Assert
import org.junit.Test

class ArtifactCoordinatesUtilTest {

  @Test
  fun `test artifact notation parsing`() {
    val dependencyId = DefaultExternalDependencyId("mygroup", "myartifact", "myversion")
    Assert.assertEquals("mygroup:myartifact:myversion",
                        parseArtifactCoordinates(dependencyId.presentableName) {
                          true
                        })

    dependencyId.classifier = "myclassifier"
    Assert.assertEquals("mygroup:myartifact:myversion",
                        parseArtifactCoordinates(dependencyId.presentableName) {
                          true
                        })

    dependencyId.packaging = "mypackaging"
    Assert.assertEquals("mygroup:myartifact:myversion",
                        parseArtifactCoordinates(dependencyId.presentableName) {
                          true
                        })

    Assert.assertEquals("mygroup:myartifact:myversion",
                        parseArtifactCoordinates(
                          DefaultExternalDependencyId("mygroup", "myartifact",
                                                      "myversion").apply { packaging = "mypackaging" }.presentableName) {
                          true
                        })

    Assert.assertEquals("myartifact:myversion",
                        parseArtifactCoordinates("myartifact:myversion") {
                          throw AssertionFailedError("artifactIdChecker shouldn't be called")
                        })

    Assert.assertEquals("mygroup:myartifact",
                        parseArtifactCoordinates("mygroup:myartifact") {
                          throw AssertionFailedError("artifactIdChecker shouldn't be called")
                        })

    Assert.assertEquals("mygroup:myartifact:myversion",
                        parseArtifactCoordinates("mygroup:myartifact:myversion@aar") {
                          throw AssertionFailedError("artifactIdChecker shouldn't be called")
                        })
  }
}