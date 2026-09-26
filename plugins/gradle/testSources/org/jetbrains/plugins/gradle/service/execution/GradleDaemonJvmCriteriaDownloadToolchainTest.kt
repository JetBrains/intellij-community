// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.common.timeoutRunBlocking
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.toJvmCriteria
import org.jetbrains.plugins.gradle.util.toJvmVendor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class  GradleDaemonJvmCriteriaDownloadToolchainTest : GradleDaemonJvmCriteriaDownloadToolchainTestCase() {

  @Test
  fun `test Given unsupported criteria When download matching JDK Then expected exception is thrown`() = timeoutRunBlocking {
    Disposer.newDisposable().use { disposable ->
      lateinit var exceptionMessage: String
      TestDialogManager.setTestDialog(TestDialog {
        exceptionMessage = it
        Messages.OK
      }, disposable)

      val daemonJvmCriteria = GradleDaemonJvmCriteria("0", "unknown".toJvmVendor())
      val actualJdkItemAndPath = GradleDaemonJvmCriteriaDownloadToolchain.pickJdkItemAndPathForMatchingCriteria(project, daemonJvmCriteria)
      Assertions.assertNull(actualJdkItemAndPath)

      Assertions.assertEquals(GradleBundle.message("gradle.toolchain.download.error.message", "0", "unknown"), exceptionMessage)
    }
  }

  @Test
  fun `test Given supported vendors When download matching JDK Then requested item contains expected values`() = timeoutRunBlocking {
    for (vendor in listOf("Oracle", "Amazon", "BellSoft", "Azul", "SAP", "Eclipse", "IBM", "GraalVM", "JetBrains")) {
      val daemonJvmCriteria = GradleDaemonJvmCriteria("21", vendor.toJvmVendor())
      val actualJdkItemAndPath = GradleDaemonJvmCriteriaDownloadToolchain.pickJdkItemAndPathForMatchingCriteria(project, daemonJvmCriteria)
      Assertions.assertNotNull(actualJdkItemAndPath)
      Assertions.assertEquals(daemonJvmCriteria, actualJdkItemAndPath!!.first.toJvmCriteria())
    }
  }

  @Test
  fun `test Given different formats Jetbrains JDK vendor When download matching JDK Then requested item contains expected values`() = timeoutRunBlocking {
    for (vendor in listOf("JetBrains", "jetbrains", "JETBRAINS", " JetBrains ")) {
      val daemonJvmCriteria = GradleDaemonJvmCriteria("17", vendor.toJvmVendor())
      val actualJdkItemAndPath = GradleDaemonJvmCriteriaDownloadToolchain.pickJdkItemAndPathForMatchingCriteria(project, daemonJvmCriteria)
      Assertions.assertNotNull(actualJdkItemAndPath)
      Assertions.assertEquals(daemonJvmCriteria, actualJdkItemAndPath!!.first.toJvmCriteria())
    }
  }

  @Test
  fun `test Given different formats of Azul JDK vendor When download matching JDK Then requested item contains expected values`() = timeoutRunBlocking {
    for (vendor in listOf("Azul Systems", "Azul Zulu", "Azul", "Zulu", "azul", "zulu")) {
      val daemonJvmCriteria = GradleDaemonJvmCriteria("17", vendor.toJvmVendor())
      val actualJdkItemAndPath = GradleDaemonJvmCriteriaDownloadToolchain.pickJdkItemAndPathForMatchingCriteria(project, daemonJvmCriteria)
      Assertions.assertNotNull(actualJdkItemAndPath)
      Assertions.assertEquals(daemonJvmCriteria, actualJdkItemAndPath!!.first.toJvmCriteria())
    }
  }
}