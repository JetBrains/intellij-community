// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.descriptor

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.IconTestUtil
import com.intellij.util.PsiIconUtil
import javax.swing.Icon

internal class PluginDescriptorTest : LightJavaCodeInsightFixtureTestCase() {

  fun testPluginDescriptorFileIcon() {
    val pluginXml = myFixture.configureByText("plugin.xml", "<idea-plugin></idea-plugin>")
    assertPsiIcon(pluginXml, AllIcons.Nodes.Plugin)

    val v2PluginXmlPackage = myFixture.configureByText("plugin_v2.xml", "<idea-plugin package=\"dummy\"></idea-plugin>")
    assertPsiIcon(v2PluginXmlPackage, DevkitCoreIcons.PluginV2)

    val v2PluginXmlContent = myFixture.configureByText("plugin_v2.xml", "<idea-plugin><content/></idea-plugin>")
    assertPsiIcon(v2PluginXmlContent, DevkitCoreIcons.PluginV2)

    val v2PluginXmlDependencies = myFixture.configureByText("plugin_v2.xml", "<idea-plugin><dependencies/></idea-plugin>")
    assertPsiIcon(v2PluginXmlDependencies, DevkitCoreIcons.PluginV2)
  }

  private fun assertPsiIcon(psiElement: PsiElement, expectedIcon: Icon) {
    val iconFromProviders = PsiIconUtil.getIconFromProviders(psiElement, 0)
    assertNotNull(iconFromProviders)
    val unwrapIcon = IconTestUtil.unwrapIcon(iconFromProviders!!)
    assertEquals(expectedIcon, unwrapIcon)
  }
}