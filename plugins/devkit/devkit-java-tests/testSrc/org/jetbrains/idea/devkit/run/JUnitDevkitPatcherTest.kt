// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.util.lang.JavaVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test

class JUnitDevkitPatcherTest : BareTestFixtureTestCase() {
  @Rule @JvmField val projectRule = ProjectRule()

  private lateinit var file: VirtualFile

  @After fun tearDown() {
    if (this::file.isInitialized) {
      runWriteActionAndWait { file.delete(this) }
    }
  }

  @Test fun jdk17AddOpens() {
    val module = projectRule.module
    runWriteActionAndWait {
      file = ModuleRootManager.getInstance(module).contentRoots[0].createChildData(this, "OpenedPackages.txt")
      file.setBinaryContent("""
          --add-opens=java.base/java.io=ALL-UNNAMED
          --add-opens=java.base/java.lang=ALL-UNNAMED
          --add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED
          --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED
          --add-opens=java.desktop/sun.lwawt=ALL-UNNAMED
          """.trimIndent().toByteArray())
    }
    val jdk = IdeaTestUtil.getMockJdk(JavaVersion.parse("17.0.1"))
    val parametersList = ParametersList()

    JUnitDevKitPatcher.appendAddOpensWhenNeeded(projectRule.project, jdk, parametersList)

    val awtPackage = when {
      SystemInfo.isWindows -> "sun.awt.windows"
      SystemInfo.isMac -> "sun.lwawt"
      else -> "sun.awt.X11"
    }
    assertThat(parametersList.list).containsExactly(
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.desktop/${awtPackage}=ALL-UNNAMED")
  }
}
