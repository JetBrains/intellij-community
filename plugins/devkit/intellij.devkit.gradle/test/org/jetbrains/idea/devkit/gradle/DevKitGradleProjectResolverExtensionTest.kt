// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import junit.framework.TestCase
import java.nio.file.Files

internal class DevKitGradleProjectResolverExtensionTest : TestCase() {

  fun testModelIsFetchedBeforeTaskExecution() {
    val modelProvider = DevKitGradleProjectResolverExtension().modelProviders.single()

    assertEquals(GradleModelFetchPhase.PROJECT_LOADED_PHASE, modelProvider.phase)
  }

  fun testReadsProductReleaseCatalog() {
    val file = Files.createTempFile("product-releases", ".txt")
    try {
      Files.writeString(
        file,
        """
          IU${'\t'}2025.2.6${'\t'}RELEASE
          IU${'\t'}253.123${'\t'}EAP
          IC-2024.3
          IC${'\t'}2024.3
          IC${'\t'}2024.3${'\t'}RELEASE${'\t'}unexpected
        """.trimIndent(),
      )

      assertEquals(
        mapOf(
          "IU" to listOf(
            IntelliJPlatformProductRelease("2025.2.6", "RELEASE"),
            IntelliJPlatformProductRelease("253.123", "EAP"),
          ),
        ),
        file.toString().readProductReleases(),
      )
    }
    finally {
      Files.deleteIfExists(file)
    }
  }
}
