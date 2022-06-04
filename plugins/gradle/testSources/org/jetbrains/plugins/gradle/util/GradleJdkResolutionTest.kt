// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.*
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver.TestUnknownSdkFixMode.TEST_DOWNLOADABLE_FIX
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver.TestUnknownSdkFixMode.TEST_LOCAL_FIX
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_DIRECTORY_PATH_KEY
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GradleJdkResolutionTest : GradleJdkResolutionTestCase() {
  @Test
  fun `test simple gradle jvm resolution`() {
    withGradleProperties(externalProjectPath, java = latestSdk) {
      assertGradleJvmSuggestion(expected = USE_GRADLE_JAVA_HOME)
    }
    withRegisteredSdks(earliestSdk, latestSdk, unsupportedSdk) {
      withGradleLinkedProject(java = earliestSdk) {
        assertGradleJvmSuggestion(expected = earliestSdk)
      }
    }
    withRegisteredSdk(latestSdk, isProjectSdk = true) {
      assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
    }
    environment.withVariables(JAVA_HOME to latestSdk.homePath) {
      assertGradleJvmSuggestion(expected = USE_JAVA_HOME)
    }
    withRegisteredSdks(earliestSdk, latestSdk, unsupportedSdk) {
      assertGradleJvmSuggestion(expected = latestSdk)
    }
    assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
  }

  @Test
  fun `test gradle jvm resolution (heuristic suggestion)`() {
    TestUnknownSdkResolver.unknownSdkFixMode = TEST_LOCAL_FIX
    assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
    TestUnknownSdkResolver.unknownSdkFixMode = TEST_DOWNLOADABLE_FIX
    assertGradleJvmSuggestion(expected = { TestSdkGenerator.getCurrentSdk() }, expectsSdkRegistration = true)
  }

  @Test
  fun `test gradle jvm resolution (linked project)`() {
    registerSdks(earliestSdk, latestSdk, unsupportedSdk)
    withGradleLinkedProject(java = earliestSdk) {
      assertGradleJvmSuggestion(expected = earliestSdk)
    }
    withGradleLinkedProject(java = latestSdk) {
      assertGradleJvmSuggestion(expected = latestSdk)
    }
    withGradleLinkedProject(java = unsupportedSdk) {
      assertGradleJvmSuggestion(expected = unsupportedSdk)
    }
  }

  @Test
  fun `test gradle jvm resolution (project sdk)`() {
    withRegisteredSdk(earliestSdk, isProjectSdk = true) {
      assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
    }
    withRegisteredSdk(latestSdk, isProjectSdk = true) {
      assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
    }
    withRegisteredSdk(unsupportedSdk, isProjectSdk = true) {
      assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
    }
  }

  @Test
  fun `test gradle jvm resolution (java home)`() {
    environment.variables(JAVA_HOME to earliestSdk.homePath)
    assertGradleJvmSuggestion(expected = USE_JAVA_HOME)
    environment.variables(JAVA_HOME to latestSdk.homePath)
    assertGradleJvmSuggestion(expected = USE_JAVA_HOME)
    environment.variables(JAVA_HOME to unsupportedSdk.homePath)
    assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
  }

  @Test
  fun `test gradle jvm resolution (gradle properties)`() {
    withGradleProperties(externalProjectPath, java = earliestSdk) {
      assertGradleJvmSuggestion(expected = USE_GRADLE_JAVA_HOME)
    }
    withGradleProperties(externalProjectPath, java = latestSdk) {
      assertGradleJvmSuggestion(expected = USE_GRADLE_JAVA_HOME)
    }
    withGradleProperties(externalProjectPath, java = unsupportedSdk) {
      assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
    }
  }

  @Test
  fun `test gradle properties resolution (project properties)`() {
    assertGradleProperties(java = null)
    withGradleProperties(externalProjectPath, java = earliestSdk) {
      assertGradleProperties(java = earliestSdk)
    }
    withGradleProperties(externalProjectPath, java = latestSdk) {
      assertGradleProperties(java = latestSdk)
    }
    withGradleProperties(externalProjectPath, java = null) {
      assertGradleProperties(java = null)
    }
  }

  @Test
  fun `test gradle properties resolution (user_home properties)`() {
    environment.properties(USER_HOME to userHome)
    withGradleProperties(userCache, java = earliestSdk) {
      assertGradleProperties(java = earliestSdk)
      withGradleProperties(externalProjectPath, java = latestSdk) {
        assertGradleProperties(java = earliestSdk)
      }
    }
    withGradleProperties(userCache, java = latestSdk) {
      assertGradleProperties(java = latestSdk)
      withGradleProperties(externalProjectPath, java = null) {
        assertGradleProperties(java = latestSdk)
      }
    }
    withGradleProperties(userCache, java = null) {
      assertGradleProperties(java = null)
      withGradleProperties(externalProjectPath, java = latestSdk) {
        assertGradleProperties(java = latestSdk)
      }
    }
  }

  @Test
  fun `test gradle properties resolution (GRADLE_USER_HOME properties)`() {
    environment.variables(SYSTEM_DIRECTORY_PATH_KEY to gradleUserHome)
    withGradleProperties(gradleUserHome, java = earliestSdk) {
      assertGradleProperties(java = earliestSdk)
      withGradleProperties(externalProjectPath, java = latestSdk) {
        assertGradleProperties(java = earliestSdk)
      }
    }
    withGradleProperties(gradleUserHome, java = latestSdk) {
      assertGradleProperties(java = latestSdk)
      withGradleProperties(externalProjectPath, java = null) {
        assertGradleProperties(java = latestSdk)
      }
    }
    withGradleProperties(gradleUserHome, java = null) {
      assertGradleProperties(java = null)
      withGradleProperties(externalProjectPath, java = latestSdk) {
        assertGradleProperties(java = latestSdk)
      }
    }
  }

  @Test
  fun `test gradle properties resolution (Idea gradle user home properties)`() {
    withServiceGradleUserHome {
      withGradleProperties(gradleUserHome, java = earliestSdk) {
        assertGradleProperties(java = earliestSdk)
        withGradleProperties(externalProjectPath, java = latestSdk) {
          assertGradleProperties(java = earliestSdk)
        }
      }
      withGradleProperties(gradleUserHome, java = latestSdk) {
        assertGradleProperties(java = latestSdk)
        withGradleProperties(externalProjectPath, java = null) {
          assertGradleProperties(java = latestSdk)
        }
      }
      withGradleProperties(gradleUserHome, java = null) {
        assertGradleProperties(java = null)
        withGradleProperties(externalProjectPath, java = latestSdk) {
          assertGradleProperties(java = latestSdk)
        }
      }
    }
  }

  @Test
  fun `test gradle properties resolution (GRADLE_USER_HOME overrides user_home)`() {
    environment.properties(USER_HOME to userHome)
    environment.variables(SYSTEM_DIRECTORY_PATH_KEY to gradleUserHome)
    withGradleProperties(gradleUserHome, java = earliestSdk) {
      withGradleProperties(userCache, java = latestSdk) {
        assertGradleProperties(java = earliestSdk)
      }
    }
    withGradleProperties(gradleUserHome, java = null) {
      withGradleProperties(userCache, java = latestSdk) {
        assertGradleProperties(java = null)
      }
    }
  }

  @Test
  fun `test suggested gradle version for sdk is compatible with target sdk`() {
    val bundledGradleApiVersion = GradleVersion.current()
    require(bundledGradleApiVersion >= GradleVersion.version("7.1"))

    assertSuggestedGradleVersionFor(null, "1.1")
    assertSuggestedGradleVersionFor(null, "1.5")

    assertSuggestedGradleVersionFor("4.10.3", "1.7")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "1.8")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "9")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "11")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "13")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "14")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "16")
    assertSuggestedGradleVersionFor(bundledGradleApiVersion, "17")

    // com.intellij.util.lang.JavaVersion.MAX_ACCEPTED_VERSION - 1
    assertSuggestedGradleVersionFor(null, "24")
  }

  @Test
  fun `test suggested oldest compatible gradle version for java version`() {
    fun getSuggestedGradle(javaFeature: Int) = suggestOldestCompatibleGradleVersion(JavaVersion.compose(javaFeature))
    assertNull(getSuggestedGradle(6))
    assertEquals("3.0", getSuggestedGradle(7)!!.version)
    assertEquals("3.0", getSuggestedGradle(8)!!.version)
    assertEquals("3.0", getSuggestedGradle(9)!!.version)
    assertEquals("3.0", getSuggestedGradle(10)!!.version)
    assertEquals("4.8", getSuggestedGradle(11)!!.version)
    assertEquals("5.4.1", getSuggestedGradle(12)!!.version)
    assertEquals("6.0", getSuggestedGradle(13)!!.version)
    assertEquals("6.3", getSuggestedGradle(14)!!.version)
    assertEquals("6.7", getSuggestedGradle(15)!!.version)
    assertEquals("7.0", getSuggestedGradle(16)!!.version)
    assertEquals("7.2", getSuggestedGradle(17)!!.version)
    assertEquals("7.2", getSuggestedGradle(24)!!.version)
  }
}