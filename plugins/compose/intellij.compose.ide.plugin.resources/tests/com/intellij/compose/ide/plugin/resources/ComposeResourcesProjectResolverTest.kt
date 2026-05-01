// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeResourcesProjectResolverTest {
  @Test
  fun `mpp source sets use compose resources layout`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/project/composeApp"),
      mppSourceSetNames = setOf("androidMain", "iosMain"),
      compiledKotlinSourceSetNames = setOf("main"),
      isAndroidKotlinProject = false,
      customComposeResourcesDirs = emptyMap(),
    )

    assertEquals(setOf("commonMain", "commonTest", "androidMain", "iosMain"), dirs.keys)
    dirs.forEach { (sourceSetName, composeResourcesDir) ->
      val (directoryPath, isCustom) = composeResourcesDir
      assertEquals("/project/composeApp/src/$sourceSetName/composeResources", directoryPath.systemIndependentPath)
      assertFalse(isCustom)
    }
  }

  @Test
  fun `non mpp Kotlin source sets use Gradle source set layout`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/project/desktopApp"),
      mppSourceSetNames = null,
      compiledKotlinSourceSetNames = setOf("main", "test"),
      isAndroidKotlinProject = false,
      customComposeResourcesDirs = emptyMap(),
    )

    assertEquals(setOf("main", "test"), dirs.keys)
    assertEquals("/project/desktopApp/src/main/composeResources", dirs["main"]?.first?.systemIndependentPath)
    assertEquals(false, dirs["main"]?.second)
    assertEquals("/project/desktopApp/src/test/composeResources", dirs["test"]?.first?.systemIndependentPath)
    assertEquals(false, dirs["test"]?.second)
    assertNull(dirs["commonMain"])
    assertNull(dirs["commonTest"])
  }

  @Test
  fun `android Kotlin projects use Android main source set layout`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/nestedProject/app/androidApp"),
      mppSourceSetNames = null,
      compiledKotlinSourceSetNames = setOf("debug", "debugAndroidTest", "debugUnitTest", "release"),
      isAndroidKotlinProject = true,
      customComposeResourcesDirs = emptyMap(),
    )

    assertEquals(setOf("main"), dirs.keys)
    dirs.assertDefaultComposeResourcesDir("main", "/nestedProject/app/androidApp/src/main/composeResources")
  }

  @Test
  fun `nested desktop application uses main compose resources layout`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/nestedProject/app/desktopApp"),
      mppSourceSetNames = null,
      compiledKotlinSourceSetNames = setOf("main"),
      isAndroidKotlinProject = false,
      customComposeResourcesDirs = emptyMap(),
    )

    assertEquals(setOf("main"), dirs.keys)
    dirs.assertDefaultComposeResourcesDir("main", "/nestedProject/app/desktopApp/src/main/composeResources")
  }

  @Test
  fun `nested shared module uses multiplatform compose resources layout`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/nestedProject/app/shared"),
      mppSourceSetNames = setOf("commonMain", "androidMain", "iosMain", "jsMain", "jvmMain", "wasmJsMain"),
      compiledKotlinSourceSetNames = null,
      isAndroidKotlinProject = false,
      customComposeResourcesDirs = emptyMap(),
    )

    assertEquals(setOf("commonMain", "commonTest", "androidMain", "iosMain", "jsMain", "jvmMain", "wasmJsMain"), dirs.keys)
    dirs.assertDefaultComposeResourcesDir("commonMain", "/nestedProject/app/shared/src/commonMain/composeResources")
    dirs.assertDefaultComposeResourcesDir("androidMain", "/nestedProject/app/shared/src/androidMain/composeResources")
  }

  @Test
  fun `nested web application uses webMain compose resources layout`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/nestedProject/app/webApp"),
      mppSourceSetNames = setOf("commonMain", "webMain", "jsMain", "wasmJsMain"),
      compiledKotlinSourceSetNames = null,
      isAndroidKotlinProject = false,
      customComposeResourcesDirs = emptyMap(),
    )

    assertEquals(setOf("commonMain", "commonTest", "webMain", "jsMain", "wasmJsMain"), dirs.keys)
    dirs.assertDefaultComposeResourcesDir("webMain", "/nestedProject/app/webApp/src/webMain/composeResources")
  }

  @Test
  fun `custom compose resources override defaults and add custom source sets`() {
    val dirs = composeResourcesDirsBySourceSetName(
      projectDirectory = Path.of("/project/composeApp"),
      mppSourceSetNames = null,
      compiledKotlinSourceSetNames = setOf("main"),
      isAndroidKotlinProject = false,
      customComposeResourcesDirs = mapOf(
        "main" to ("/project/composeApp/customMainResources" to true),
        "integrationTest" to ("/project/composeApp/customIntegrationResources" to true),
      ),
    )

    assertEquals(setOf("main", "integrationTest"), dirs.keys)
    assertEquals("/project/composeApp/customMainResources" to true, dirs["main"])
    assertEquals("/project/composeApp/customIntegrationResources" to true, dirs["integrationTest"])
    assertTrue(dirs.values.all { it.second })
  }

  private val String.systemIndependentPath: String
    get() = replace('\\', '/')

  private fun Map<String, Pair<String, Boolean>>.assertDefaultComposeResourcesDir(sourceSetName: String, expectedPath: String) {
    val (directoryPath, isCustom) = getValue(sourceSetName)
    assertEquals(expectedPath, directoryPath.systemIndependentPath)
    assertFalse(isCustom)
  }
}
