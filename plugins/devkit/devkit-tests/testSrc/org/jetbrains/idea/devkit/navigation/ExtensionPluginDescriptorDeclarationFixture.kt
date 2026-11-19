// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/**
 * The fixture of the tests of the extension gutter in `plugin.xml`. Create it with [extensionPluginDescriptorDeclarationFixture].
 *
 * The project has the stub of `JBList`.
 */
class ExtensionPluginDescriptorDeclarationFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Copies `plugin.xml`, checks that it has [xmlDeclarationText] and checks the extension gutter of [file].
   */
  fun doTestExtension(file: String, xmlDeclarationText: String) {
    val pluginXmlFile = fixture.copyFileToProject("plugin.xml")
    runReadActionBlocking {
      val pluginPsiFile = PsiManager.getInstance(fixture.project).findFile(pluginXmlFile)
      assertNotNull(pluginPsiFile)

      val expectedTagPosition = pluginPsiFile!!.text.indexOf(xmlDeclarationText)
      assertNotEquals(-1, expectedTagPosition)
    }

    val gutter = fixture.findGutter(file)
    runReadActionBlocking {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter, "<html><body>com.intellij.myEp<br></body></html>",
                                                    DevkitCoreIcons.Gutter.Plugin, "myEp")
    }
  }

  /**
   * Configures `plugin.xml` and checks that [file] has no gutter.
   */
  fun doTestInvalidExtension(file: String) {
    fixture.configureByFile("plugin.xml")
    assertNull(fixture.findGutter(file))
  }
}

/**
 * Creates an [ExtensionPluginDescriptorDeclarationFixture] for one test. Declare it as an instance field.
 *
 * [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun extensionPluginDescriptorDeclarationFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<ExtensionPluginDescriptorDeclarationFixture> = testFixture("extensionPluginDescriptorDeclarationFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.addClass("package com.intellij.ui.components; public class JBList {}")
  initialized(ExtensionPluginDescriptorDeclarationFixture(fixture)) {}
}
