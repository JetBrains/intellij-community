// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

object KotlinCreateFileFUSCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup("kotlin.ide.new.file", 2)

    private val pluginInfo = getPluginInfoById(KotlinIdePlugin.id)

    private val allowedTemplates = listOf(
        "Kotlin_Class",
        "Kotlin_File",
        "Kotlin_Interface",
        "Kotlin_Data_Class",
        "Kotlin_Enum",
        "Kotlin_Sealed_Class",
        "Kotlin_Annotation",
        "Kotlin_Object",
        "Kotlin_Scratch",
        "Kotlin_Script",
        "Kotlin_Worksheet"
    )

    private val newFileEvent = GROUP.registerEvent(
        "Created",
        EventFields.String("file_template", allowedTemplates),
        EventFields.PluginInfo
    )

    fun logFileTemplate(template: String) = newFileEvent.log(template.replace(' ', '_'), pluginInfo)
}
