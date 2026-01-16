// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UnresolvedDependencyIssueTest {
  @Test
  fun `test unresolved dependency during sync in offline mode has valid quick-fix`() {
    val syncIssue = UnresolvedDependencySyncIssue(
      dependencyName = "my.not.existing.dependency:gradle:1.2.3-dev",
      failureMessage = "No cached version of my.not.existing.dependency:gradle:1.2.3-dev available for offline mode.",
      projectPath = "irrelevant",
      isOfflineMode = true
    )
    val actualQuickFixes = syncIssue.quickFixes
    assertEquals(1, actualQuickFixes.size) { "Should contain just the offline mode quick-fix" }
    val offlineQuickFixActualId = actualQuickFixes[0].id
    assertEquals(
      """
        No cached version of my.not.existing.dependency:gradle:1.2.3-dev available for offline mode.
  
        Possible solution:
         - <a href="$offlineQuickFixActualId">Disable offline mode and reload the project</a>
        
      """.trimIndent(),
      syncIssue.description
    )
  }

  @Test
  fun `test unresolved dependency during build in offline mode has valid quick-fix`() {
    val syncIssue = UnresolvedDependencyBuildIssue(
      dependencyName = "my.not.existing.dependency:gradle:1.2.3-dev",
      failureMessage = "No cached version of my.not.existing.dependency:gradle:1.2.3-dev available for offline mode.",
      isOfflineMode = true
    )
    val actualQuickFixes = syncIssue.quickFixes
    assertEquals(1, actualQuickFixes.size) { "Should contain just the offline mode quick-fix" }
    val offlineQuickFixActualId = actualQuickFixes[0].id
    assertEquals(
      """
        No cached version of my.not.existing.dependency:gradle:1.2.3-dev available for offline mode.
  
        Possible solution:
         - <a href="$offlineQuickFixActualId">Disable offline mode and rerun the build</a>
        
      """.trimIndent(),
      syncIssue.description
    )
  }

  @Test
  fun `test unresolved dependency during sync has no quick-fixes`() {
    val syncIssue = UnresolvedDependencySyncIssue(
      dependencyName = "my.not.existing.dependency:gradle:1.2.3-dev",
      failureMessage = """
        Could not find my.not.existing.dependency:gradle:1.2.3-dev.
        Searched in the following locations:
          - https://some.link
        Required by:
            root project 'irrelevant'
      """.trimIndent(),
      projectPath = "irrelevant",
      isOfflineMode = false
    )
    val actualQuickFixes = syncIssue.quickFixes
    assertEquals(0, actualQuickFixes.size) { "Should not contain any quick-fixes" }
    assertEquals(
      """
        Could not find my.not.existing.dependency:gradle:1.2.3-dev.
        Searched in the following locations:
          - https://some.link
        Required by:
            root project 'irrelevant'
        
        Possible solution:
         - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
        
      """.trimIndent(),
      syncIssue.description
    )
  }

  @Test
  fun `test unresolved dependency during build has no quick-fixes`() {
    val syncIssue = UnresolvedDependencyBuildIssue(
      dependencyName = "my.not.existing.dependency:gradle:1.2.3-dev",
      failureMessage = """
        Could not find my.not.existing.dependency:gradle:1.2.3-dev.
        Searched in the following locations:
          - https://some.link
        Required by:
            root project 'irrelevant'
      """.trimIndent(),
      isOfflineMode = false
    )
    val actualQuickFixes = syncIssue.quickFixes
    assertEquals(0, actualQuickFixes.size) { "Should not contain any quick-fixes" }
    assertEquals(
      """
        Could not find my.not.existing.dependency:gradle:1.2.3-dev.
        Searched in the following locations:
          - https://some.link
        Required by:
            root project 'irrelevant'
        
        Possible solution:
         - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
        
      """.trimIndent(),
      syncIssue.description
    )
  }
}
