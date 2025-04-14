// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.*
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver.TestUnknownSdkFixMode.TEST_DOWNLOADABLE_FIX
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver.TestUnknownSdkFixMode.TEST_LOCAL_FIX
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_USER_HOME_ENV_KEY
import org.jetbrains.plugins.gradle.util.GradleConstants.USER_HOME_PROPERTY_KEY
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
    withGradleLocalProperties(externalProjectPath, java = latestSdk) {
      assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
    }
    withRegisteredSdks(earliestSdk, latestSdk, unsupportedSdk) {
      assertUnexpectedSdksRegistration {
        withGradleLinkedProject(java = earliestSdk) {
          assertGradleJvmSuggestion(expected = earliestSdk)
        }
      }
    }
    withRegisteredSdks(latestSdk) {
      withProjectSdk(latestSdk) {
        assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
      }
    }
    environment.withVariables(JAVA_HOME to latestSdk.homePath) {
      assertGradleJvmSuggestion(expected = USE_JAVA_HOME)
    }
    withRegisteredSdks(earliestSdk, latestSdk, unsupportedSdk) {
      assertUnexpectedSdksRegistration {
        assertGradleJvmSuggestion(expected = latestSdk)
      }
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
    withRegisteredSdks(earliestSdk, latestSdk, unsupportedSdk) {
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
  }

  @Test
  fun `test gradle jvm resolution (project sdk)`() {
    withRegisteredSdks(earliestSdk) {
      withProjectSdk(earliestSdk) {
        assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
      }
    }
    withRegisteredSdks(latestSdk) {
      withProjectSdk(latestSdk) {
        assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
      }
    }
    withRegisteredSdks(unsupportedSdk) {
      withProjectSdk(unsupportedSdk) {
        assertGradleJvmSuggestion(expected = USE_PROJECT_JDK)
      }
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
  fun `test gradle jvm resolution (gradle local properties)`() {
    withGradleLocalProperties(externalProjectPath, java = earliestSdk) {
      assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
    }
    withGradleLocalProperties(externalProjectPath, java = latestSdk) {
      assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
    }
    withGradleLocalProperties(externalProjectPath, java = unsupportedSdk) {
      assertGradleJvmSuggestion(expected = latestSdk, expectsSdkRegistration = true)
    }
  }

  @Test
  fun `test gradle local properties resolution (project properties)`() {
    assertGradleLocalProperties(java = null)
    withGradleLocalProperties(externalProjectPath, java = earliestSdk) {
      assertGradleLocalProperties(java = earliestSdk)
    }
    withGradleLocalProperties(externalProjectPath, java = latestSdk) {
      assertGradleLocalProperties(java = latestSdk)
    }
    withGradleLocalProperties(externalProjectPath, java = null) {
      assertGradleLocalProperties(java = null)
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
    environment.properties(USER_HOME_PROPERTY_KEY to userHome)
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
    environment.variables(GRADLE_USER_HOME_ENV_KEY to gradleUserHome)
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
    environment.properties(USER_HOME_PROPERTY_KEY to userHome)
    environment.variables(GRADLE_USER_HOME_ENV_KEY to gradleUserHome)
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
}