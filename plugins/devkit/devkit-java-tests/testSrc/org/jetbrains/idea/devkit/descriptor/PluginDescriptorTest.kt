// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.descriptor

import com.intellij.devkit.core.icons.DevkitCoreIcons.PluginV2
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.ui.IconTestUtil
import com.intellij.util.PsiIconUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import javax.swing.Icon

@TestApplication
internal class PluginDescriptorTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project
    get() = projectModel.project

  @Test
  fun testPluginDescriptorFileIcon() {
    createDescriptor("plugin.main", "plugin.xml", "<idea-plugin></idea-plugin>")
    createDescriptor("plugin.package", "plugin_v2_package.xml", "<idea-plugin package=\"dummy\"></idea-plugin>")
    createDescriptor("plugin.content", "plugin_v2_content.xml", "<idea-plugin><content/></idea-plugin>")
    createDescriptor("plugin.dependencies", "plugin_v2_dependencies.xml", "<idea-plugin><dependencies/></idea-plugin>")

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    assertFileIcon("plugin.main/resources/plugin.xml", AllIcons.Nodes.Plugin)
    assertFileIcon("plugin.package/resources/plugin_v2_package.xml", PluginV2)
    assertFileIcon("plugin.content/resources/plugin_v2_content.xml", PluginV2)
    assertFileIcon("plugin.dependencies/resources/plugin_v2_dependencies.xml", PluginV2)
  }

  private fun createDescriptor(moduleName: String, descriptorName: String, descriptorText: String) {
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory(moduleName)
    val module = PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, moduleRoot)
    val resourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
    projectModel.baseProjectDir.newVirtualFile("$moduleName/resources/$descriptorName", descriptorText.toByteArray())
  }

  private fun assertFileIcon(relativePath: String, expectedIcon: Icon) {
    assertPsiIcon(expectedIcon) {
      PsiManager.getInstance(project).findFile(projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath(relativePath)!!)!!
    }
  }

  private fun assertPsiIcon(@Suppress("SameParameterValue") expectedIcon: Icon, psiElementProvider: () -> PsiElement) {
    val unwrapIcon = runReadActionBlocking {
      val iconFromProviders = PsiIconUtil.getIconFromProviders(psiElementProvider(), 0)
      assertNotNull(iconFromProviders)
      IconTestUtil.unwrapIcon(iconFromProviders!!)
    }
    assertEquals(expectedIcon, unwrapIcon)
  }
}