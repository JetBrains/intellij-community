// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.filters.HyperlinkInfoFactory
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.jps.model.java.JavaResourceRootType

internal val ModulePattern = """(intellij|kotlin|fleet|android)(\.[-\w]+)+""".toRegex()

internal class ModulePathFilterProvider : ConsoleFilterProvider {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment || !application.isInternal) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun getDefaultFilters(project: Project): Array<Filter> {
    return if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) arrayOf(ModulePathFilter(project)) else emptyArray()
  }
}

internal class ModulePathFilter(private val project: Project) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val matchResult = ModulePattern.findAll(line)
    val textStartOffset = entireLength - line.length
    val moduleManager = ModuleManager.getInstance(project)

    val items = matchResult
      .mapNotNull { resultItem ->
        val moduleName = resultItem.value
        val module = moduleManager.findModuleByName(moduleName) ?: return@mapNotNull null

        val textRange = resultItem.range
        Filter.ResultItem(
          /* highlightStartOffset = */ textStartOffset + textRange.first,
          /* highlightEndOffset = */ textStartOffset + textRange.last + 1,
          /* hyperlinkInfo = */ ModuleFilesHyperlinkInfo(module),
        )
      }
      .toList()

    return if (items.isEmpty()) null else Filter.Result(items)
  }
}

/**
 * Lazy hyperlink info that navigates to module files when clicked.
 * By default, navigates to module descriptor XML (e.g., intellij.foo.bar.xml) if exists, otherwise .iml.
 * When registry key `devkit.module.hyperlink.show.chooser` is enabled,
 * shows a chooser if multiple files exist (iml + descriptor XML).
 */
internal class ModuleFilesHyperlinkInfo(private val module: Module) : HyperlinkInfoBase() {
  override fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?) {
    if (Registry.`is`("devkit.module.hyperlink.show.chooser")) {
      val descriptorFiles = findDescriptorFiles(module).toList()
      val files = listOfNotNull(module.moduleFile) + descriptorFiles
      if (files.size > 1) {
        (HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(files, 0, project) as HyperlinkInfoBase)
          .navigate(project, hyperlinkLocationPoint)
        return
      }
      // files.size <= 1, use the collected result
      navigateToFile(project, descriptorFiles.firstOrNull() ?: module.moduleFile ?: return)
    }
    else {
      navigateToFile(project, findDescriptorFiles(module).firstOrNull() ?: module.moduleFile ?: return)
    }
  }
}

private fun navigateToFile(project: Project, file: VirtualFile) {
  OpenFileHyperlinkInfo(
    /* project = */ project,
    /* file = */ file,
    /* documentLine = */ 0,
    /* documentColumn = */ 0,
    /* isUseBrowser = */ false,
  ).navigate(project)
}

private fun findDescriptorFiles(module: Module): Sequence<VirtualFile> {
  return sequence {
    val rootManager = ModuleRootManager.getInstance(module)
    for (resourceRoot in rootManager.getSourceRoots(JavaResourceRootType.RESOURCE)) {
      resourceRoot.findFileByRelativePath("META-INF/plugin.xml")?.let { yield(it) }
      resourceRoot.findChild("${module.name}.xml")?.let { yield(it) }
    }
  }
}