// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.descriptor

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons.Nodes.Plugin
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.ui.IconTestUtil
import com.intellij.util.PsiIconUtil
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import javax.swing.Icon

@TestApplication
internal class PluginDescriptorSplitModeIconsTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project
    get() = projectModel.project

  @Test
  @RegistryKey(key = "devkit.plugin.directory.icons", value = "true")
  fun `descriptor and directory icons follow split mode kind`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    try {
      createModuleDescriptor("module.shared", "module.shared.xml", """
        <idea-plugin>
        </idea-plugin>
      """.trimIndent())
      createSharedPluginDescriptor("""
        <idea-plugin>
        </idea-plugin>
      """.trimIndent())
      createFrontendPluginDescriptor("""
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent())
      createModuleDescriptor("module.backend", "module.backend.xml", """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent())
      IndexingTestUtil.waitUntilIndexesAreReady(project)

      assertFileIcon("shared.plugin/resources/META-INF/plugin.xml", Plugin)
      assertFileIcon("module.shared/resources/module.shared.xml", DevkitCoreIcons.SharedModule)
      assertFileIcon("frontend.plugin/resources/META-INF/plugin.xml", DevkitCoreIcons.FrontendModule)
      assertFileIcon("module.backend/resources/module.backend.xml", DevkitCoreIcons.BackendModule)
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    }
  }

  private fun createModuleDescriptor(moduleName: String, descriptorName: String, descriptorText: String) {
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory(moduleName)
    val module = PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, moduleRoot)
    val resourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
    projectModel.baseProjectDir.newVirtualFile("$moduleName/resources/$descriptorName", descriptorText.toByteArray())
  }

  private fun createFrontendPluginDescriptor(descriptorText: String) {
    val moduleName = "frontend.plugin"
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory(moduleName)
    val module = PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, moduleRoot)
    val resourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
    val descriptorFile =
      projectModel.baseProjectDir.newVirtualFile("$moduleName/resources/META-INF/plugin.xml", descriptorText.toByteArray())
    PluginBuildConfiguration.getInstance(module)!!.setPluginXmlFromVirtualFile(descriptorFile)
  }

  private fun createSharedPluginDescriptor(descriptorText: String) {
    val moduleName = "shared.plugin"
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory(moduleName)
    val module = PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, moduleRoot)
    val resourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
    val descriptorFile =
      projectModel.baseProjectDir.newVirtualFile("$moduleName/resources/META-INF/plugin.xml", descriptorText.toByteArray())
    PluginBuildConfiguration.getInstance(module)!!.setPluginXmlFromVirtualFile(descriptorFile)
  }

  private fun assertFileIcon(relativePath: String, expectedIcon: Icon) {
    assertIcon(expectedIcon) {
      PsiManager.getInstance(project).findFile(projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath(relativePath)!!)!!
    }
  }

  private fun assertIcon(expectedIcon: Icon, psiElementProvider: () -> com.intellij.psi.PsiElement) {
    val unwrappedIcon = runReadActionBlocking {
      val iconFromProviders = PsiIconUtil.getIconFromProviders(psiElementProvider(), 0)
      checkNotNull(iconFromProviders)
      IconTestUtil.unwrapIcon(iconFromProviders)
    }
    assertEquals(expectedIcon, unwrappedIcon)
  }
}
