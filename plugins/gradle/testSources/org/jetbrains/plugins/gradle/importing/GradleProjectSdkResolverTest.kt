// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.Companion.assertNewlyRegisteredSdks
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.Companion.assertUnexpectedSdksRegistration
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.Companion.withRegisteredSdks
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Ignore
import org.junit.Test

class GradleProjectSdkResolverTest : GradleProjectSdkResolverTestCase() {

  @Test
  @Ignore(value = "IDEA-369675")
  fun `test setup of project sdk for newly opened project`() = runBlocking {
    val jdk = resolveRealTestSdk()
    createGradleSubProject()

    environment.withVariables(JAVA_HOME to jdk.homePath) {
      withRegisteredSdks(jdk) {
        assertUnexpectedSdksRegistration {
          loadProject()
          assertSdks(jdk, "project", "project.main", "project.test")
        }
      }
    }
  }

  @Test
  @Ignore(value = "IDEA-369675")
  fun `test setup of project sdk for newly opened project in clean IDEA`() = runBlocking {
    val jdk = resolveRealTestSdk()
    createGradleSubProject()

    environment.withVariables(JAVA_HOME to jdk.homePath) {
      assertUnexpectedSdksRegistration {
        assertNewlyRegisteredSdks({ jdk }, isAssertSdkName = false) {
          loadProject()
          assertSdks(jdk, "project", "project.main", "project.test", isAssertSdkName = false)
        }
      }
    }
  }

  @Test
  @Ignore(value = "IDEA-369675")
  fun `test project-module sdk replacing`() = runBlocking {
    val jdk = resolveRealTestSdk()
    val sdk = TestSdkGenerator.createNextSdk()
    createGradleSubProject()

    environment.withVariables(JAVA_HOME to jdk.homePath) {
      withRegisteredSdks(jdk, sdk) {
        assertUnexpectedSdksRegistration {
          loadProject()
          assertSdks(jdk, "project", "project.main", "project.test")

          withProjectSdk(sdk) {
            assertSdks(sdk, "project", "project.main", "project.test")

            reloadProject()
            assertProjectSdk(sdk) // Bug IDEA-258496 should be Gradle JVM (jdk)
            assertModuleSdks(jdk, "project", "project.main", "project.test")
          }
        }
      }
    }
  }

  @Test
  @TargetVersions("8.8+")
  @Ignore(value = "IDEA-369675")
  fun `test project using Daemon Jvm criteria`() = runBlocking {
    val jdk = resolveRealTestSdk()
    val sdk = TestSdkGenerator.createNextSdk()
    createGradleSubProject()
    createDaemonJvmPropertiesFile(jdk)

    environment.withVariables(JAVA_HOME to sdk.homePath) {
      withRegisteredSdks(jdk, sdk) {
        assertUnexpectedSdksRegistration {
          loadProject()
          assertSdks(jdk, "project", "project.main", "project.test")
        }
      }
    }
  }
}