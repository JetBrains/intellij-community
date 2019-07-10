// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.PublicFieldBasedOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.project.Project

class LastVisitedTimestampsTopHitProvider : OptionsSearchTopHitProvider.ProjectLevelProvider {
  override fun getId(): String = "timestamps"

  override fun getOptions(project: Project): List<OptionDescription> =
    listOf<OptionDescription>(object : PublicFieldBasedOptionDescription(IdeBundle.message("last.visited.timestamps.option"),
                                                                         null,
                                                                         IdeDocumentHistoryImpl.LAST_VISITED_TIMESTAMP_OPTION) {
      override fun getInstance(): Any = IdeDocumentHistoryImpl.getInstance(project)
    })
}