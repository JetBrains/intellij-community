// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import junit.framework.AssertionFailedError
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.junit.Assert.assertEquals
import org.junit.Test

class GradleAttachSourcesProviderUnitTest {

  @Test
  fun `test sources artifact notation parsing`() {
    val dependencyId = DefaultExternalDependencyId("mygroup", "myartifact", "myversion")
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    dependencyId.classifier = "myclassifier"
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    dependencyId.packaging = "mypackaging"
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(
                   DefaultExternalDependencyId("mygroup", "myartifact", "myversion").apply { packaging = "mypackaging" }.presentableName) {
                   true
                 })

    assertEquals("myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation("myartifact:myversion") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation("mygroup:myartifact") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation("mygroup:myartifact:myversion@aar") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })
  }
}
