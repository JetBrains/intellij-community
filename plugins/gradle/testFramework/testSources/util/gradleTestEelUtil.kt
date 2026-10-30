// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.util

import com.intellij.gradle.testFramework.importing.GradleImportingTestCase
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.junit.Assume

fun GradleImportingTestCase.assumeOnLocalEnvironmentOnly(cause: String) {
  Assume.assumeTrue("Unable to run the test on a non-local environment: $cause", LocalEelDescriptor == myProject.getEelDescriptor())
}
