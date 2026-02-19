// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import org.ec4j.core.ResourceProperties
import org.editorconfig.configmanagement.extended.EditorConfigIntellijNameUtil
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind
import org.editorconfig.configmanagement.extended.IntellijPropertyKindMap

object EditorConfigUsagesCollector : CounterUsagesCollector() {
  private enum class OptionType {
    Standard, IntelliJ, Other
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private val GROUP = EventLogGroup("editorconfig", 2)
  private val EDITOR_CONFIG_USED: EventId3<FileType, OptionType, Int> =
    GROUP.registerEvent("editorconfig.applied", EventFields.FileType,
                        Enum("property", OptionType::class.java),
                        EventFields.Count)

  fun logEditorConfigUsed(file: PsiFile, properties: ResourceProperties) {
    properties.properties.keys
      .groupingBy { getOptionType(it) }
      .eachCount()
      .forEach { (optionType, count) -> EDITOR_CONFIG_USED.log(file.project, file.fileType, optionType, count) }
  }

  private fun getOptionType(optionKey: String): OptionType {
    val propertyKind = IntellijPropertyKindMap.getPropertyKind(optionKey)
    return if (propertyKind == EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD) {
      OptionType.Standard
    }
    else if (optionKey.startsWith(EditorConfigIntellijNameUtil.IDE_PREFIX)) {
      OptionType.IntelliJ
    }
    else {
      OptionType.Other
    }
  }
}