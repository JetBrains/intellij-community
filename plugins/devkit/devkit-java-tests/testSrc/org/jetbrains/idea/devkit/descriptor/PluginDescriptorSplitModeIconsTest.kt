// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.descriptor

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.ide.IconProvider
import com.intellij.icons.AllIcons.Nodes.Plugin
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.IconTestUtil
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.CompositeIcon
import com.intellij.util.PsiIconUtil
import kotlinx.coroutines.runBlocking
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
  @RegistryKey(key = "devkit.split.mode.custom.icons", value = "false")
  fun `descriptor and directory icons use fast split mode kind when accurate icons are disabled`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    try {
      createSplitModeIconFixtures()

      assertFileIcon("shared.plugin/resources/META-INF/plugin.xml", Plugin)
      assertFileIcon("module.shared/resources/module.shared.xml", DevkitCoreIcons.SharedModule)
      assertFileIcon("frontend.plugin/resources/META-INF/plugin.xml", DevkitCoreIcons.FrontendModule)
      assertFileIcon(
        "intellij.transitive.frontend/resources/intellij.transitive.frontend.xml",
        DevkitCoreIcons.FrontendModule,
      )
      assertFileIcon("module.backend/resources/module.backend.xml", DevkitCoreIcons.BackendModule)
      assertFileIcon("transitive.consumer/resources/META-INF/plugin.xml", Plugin)
      assertDirectoryIcon("frontend.plugin", DevkitCoreIcons.FrontendModule)
      assertDirectoryIcon("transitive.consumer", Plugin)
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    }
  }

  @Test
  @RegistryKey(key = "devkit.plugin.directory.icons", value = "true")
  @RegistryKey(key = "devkit.split.mode.custom.icons", value = "true")
  fun `descriptor and directory icons resolve accurate split mode kind when enabled`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    try {
      createSplitModeIconFixtures()

      assertFileIcon("transitive.consumer/resources/META-INF/plugin.xml", DevkitCoreIcons.FrontendModule)
      assertDirectoryIcon("transitive.consumer", DevkitCoreIcons.FrontendModule)
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    }
  }

  private fun createSplitModeIconFixtures() {
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
    createModuleDescriptor("intellij.transitive.frontend", "intellij.transitive.frontend.xml", """
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
    createPluginDescriptor("transitive.consumer", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.transitive.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent())
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private fun createModuleDescriptor(moduleName: String, descriptorName: String, descriptorText: String) {
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory(moduleName)
    val module = PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, moduleRoot)
    val resourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
    projectModel.baseProjectDir.newVirtualFile("$moduleName/resources/$descriptorName", descriptorText.toByteArray())
  }

  private fun createFrontendPluginDescriptor(descriptorText: String) {
    createPluginDescriptor("frontend.plugin", descriptorText)
  }

  private fun createSharedPluginDescriptor(descriptorText: String) {
    createPluginDescriptor("shared.plugin", descriptorText)
  }

  private fun createPluginDescriptor(moduleName: String, descriptorText: String) {
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
      val file = PsiManager.getInstance(project).findFile(projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath(relativePath)!!)!!
      PsiIconUtil.getIconFromProviders(file, 0)
    }
  }

  private fun assertDirectoryIcon(relativePath: String, expectedIcon: Icon) {
    assertIcon(expectedIcon) {
      val directory = PsiManager.getInstance(project)
        .findDirectory(projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath(relativePath)!!)!!
      getScaffoldingDirectoryIconProvider().getIcon(directory, 0)
    }
  }

  private fun getScaffoldingDirectoryIconProvider(): IconProvider {
    return IconProvider.EXTENSION_POINT_NAME.extensionList.single {
      it.javaClass.name == "com.intellij.devkit.scaffolding.ScaffoldingDirectoryIconProvider"
    }
  }

  private fun assertIcon(expectedIcon: Icon, iconProvider: () -> Icon?) {
    val iconFromProviders = runReadActionBlocking {
      iconProvider()
    }
    checkNotNull(iconFromProviders)
    awaitDeferredIcons(iconFromProviders)
    val unwrappedIcon = invokeAndWaitIfNeeded {
      IconTestUtil.unwrapIcon(iconFromProviders)
    }
    assertEquals(expectedIcon, unwrappedIcon)
  }

  private fun awaitDeferredIcons(icon: Icon) {
    when (icon) {
      is DeferredIconImpl<*> -> runBlocking {
        icon.awaitEvaluation()
      }
      is CompositeIcon -> {
        for (i in 0 until icon.iconCount) {
          val nestedIcon = icon.getIcon(i) ?: continue
          awaitDeferredIcons(nestedIcon)
        }
      }
      is RetrievableIcon -> awaitDeferredIcons(icon.retrieveIcon())
    }
  }
}
