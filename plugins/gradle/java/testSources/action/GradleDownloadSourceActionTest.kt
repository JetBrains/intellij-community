// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import junit.framework.AssertionFailedError
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.junit.Assert.assertEquals
import org.junit.Test

class GradleDownloadSourceActionTest {

  @Test
  fun `test sources artifact notation parsing`() {
    val dependencyId = DefaultExternalDependencyId("mygroup", "myartifact", "myversion")
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    dependencyId.classifier = "myclassifier"
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    dependencyId.packaging = "mypackaging"
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation(
                   DefaultExternalDependencyId("mygroup", "myartifact", "myversion").apply { packaging = "mypackaging" }.presentableName) {
                   true
                 })

    assertEquals("myartifact:myversion:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation("myartifact:myversion") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation("mygroup:myartifact") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleDownloadSourceAction.getSourceArtifactNotation("mygroup:myartifact:myversion@aar") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })
  }
}
