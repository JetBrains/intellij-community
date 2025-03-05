// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project

private val ModulePattern = """(intellij|kotlin|fleet|android)(\.[-\w]+)+""".toRegex()

internal class ModulePathFilterProvider : ConsoleFilterProvider {

  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode
        || application.isHeadlessEnvironment
        || !application.isInternal)
      throw ExtensionNotApplicableException.create()
  }

  override fun getDefaultFilters(project: Project): Array<Filter> =
    if (IntelliJProjectUtil.isIntelliJPlatformProject(project))
      arrayOf(ModulePathFilter(project))
    else
      emptyArray()

  private class ModulePathFilter(private val project: Project) : Filter {

    override fun applyFilter(
      line: String,
      entireLength: Int,
    ): Filter.Result? {
      val matchResult = ModulePattern.findAll(line)
      val textStartOffset = entireLength - line.length
      val moduleManager = ModuleManager.getInstance(project)

      val items = matchResult.mapNotNull { resultItem ->
        val moduleName = resultItem.value

        val module = moduleManager.findModuleByName(moduleName)
                     ?: return@mapNotNull null

        val moduleFile = module.moduleFile
                         ?: return@mapNotNull null

        val textRange = resultItem.range
        Filter.ResultItem(
          /* highlightStartOffset = */ textStartOffset + textRange.first,
          /* highlightEndOffset = */ textStartOffset + textRange.last + 1,
          /* hyperlinkInfo = */
          OpenFileHyperlinkInfo(
            /* project = */ project,
            /* file = */ moduleFile,
            /* documentLine = */ 0,
            /* documentColumn = */ 0,
            /* isUseBrowser = */ false,
          ),
        )
      }.toList()

      return if (items.isNotEmpty())
        Filter.Result(items)
      else
        null
    }
  }
}