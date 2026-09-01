// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.lang.reflect.Proxy
import java.nio.file.Files

internal class DevKitGradleProjectResolverExtensionTest : TestCase() {

  fun testModelIsFetchedBeforeTaskExecution() {
    val modelProvider = DevKitGradleProjectResolverExtension().modelProviders.single()

    assertEquals(GradleModelFetchPhase.PROJECT_LOADED_PHASE, modelProvider.phase)
  }

  fun testFailedFetchWithoutIntelliJPlatformModelIsIgnored() {
    val context = Proxy.newProxyInstance(
      javaClass.classLoader,
      arrayOf(ProjectResolverContext::class.java),
    ) { _, method, arguments ->
      when (method.name) {
        "hasModulesWithModel" -> {
          assertSame(IntelliJPlatformGradleModel::class.java, arguments.single())
          false
        }
        else -> error("Unexpected ProjectResolverContext call: ${method.name}")
      }
    } as ProjectResolverContext

    IntelliJPlatformGradleSyncListener().onModelFetchFailed(context, RuntimeException("Expected sync failure"))
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
