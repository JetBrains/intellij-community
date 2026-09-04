// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Properties
import kotlin.io.path.inputStream

/**
 * The test data project keeps a Gradle wrapper, and the wrapper must declare [TARGET_GRADLE_VERSION].
 *
 * `GradleImportingTestCase` runs with `DistributionType.DEFAULT_WRAPPED`, so the import reads the wrapper
 * of the project. `KotlinGradleImportingTestCase.configureByFiles` copies each test data file into the
 * project, and it writes this file over the wrapper that the test framework generates. A different version
 * here makes [ComposeResourcesTestCase] download another Gradle distribution.
 */
class ComposeResourcesTestDataWrapperTest {

  @Test
  fun `test data wrapper declares the target Gradle version`() {
    val wrapperProperties = composeResourcesProjectRoot()
      .resolve("gradle/wrapper/gradle-wrapper.properties")
    val properties = Properties()
    wrapperProperties.inputStream().use(properties::load)

    val distributionUrl = properties.getProperty("distributionUrl")
    assertEquals(
      "$wrapperProperties must declare Gradle $TARGET_GRADLE_VERSION",
      "https://services.gradle.org/distributions/gradle-$TARGET_GRADLE_VERSION-bin.zip",
      distributionUrl,
    )
  }
}
