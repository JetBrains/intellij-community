// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.testFramework.roots.ui.configuration.TestSdkGenerator

class GradleJdkNonblockingUtilTest : GradleJdkNonblockingUtilTestCase() {
  fun `test nonblocking jdk resolution (gradle properties)`() {
    val sdk = TestSdkGenerator.createNextSdk()
    assertSdkInfo(SdkInfo.Undefined, USE_GRADLE_JAVA_HOME)
    GradleJdkResolutionTestCase.withGradleProperties(externalProjectPath, sdk) {
      assertSdkInfo(sdk.versionString!!, sdk.homePath!!, USE_GRADLE_JAVA_HOME)
    }
  }

  fun `test nonblocking jdk resolution (gradle local properties)`() {
    val sdk = TestSdkGenerator.createNextSdk()
    assertSdkInfo(SdkInfo.Undefined, USE_GRADLE_LOCAL_JAVA_HOME)
    GradleJdkResolutionTestCase.withGradleLocalProperties(externalProjectPath, sdk) {
      assertSdkInfo(sdk.versionString!!, sdk.homePath!!, USE_GRADLE_LOCAL_JAVA_HOME)
    }
  }
}