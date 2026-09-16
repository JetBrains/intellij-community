// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

object J2KFusCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup("kotlin.ide.j2k", 6)

    private val sourceTypeField = EventFields.String("source_type", ConversionType.entries.map { it.text })
    private val conversionTimeField = EventFields.Long("conversion_time_ms")
    private val linesCountField = EventFields.RoundedInt("lines_count")
    private val filesCountField = EventFields.RoundedInt("files_count")

    /** Absent for a conversion that cannot touch other files, such as copy-paste. */
    private val updateExternalUsagesField = EventFields.Boolean("update_external_usages")
    private val pluginInfoField = EventFields.PluginInfo

    private val event = GROUP.registerVarargEvent(
        "Conversion",
        sourceTypeField,
        conversionTimeField,
        linesCountField,
        filesCountField,
        updateExternalUsagesField,
        pluginInfoField,
    )

    fun log(
        type: ConversionType,
        conversionTime: Long,
        linesCount: Int,
        filesCount: Int,
        updateExternalUsages: Boolean? = null,
    ): Unit = event.log(
        buildList {
            add(sourceTypeField.with(type.text))
            add(conversionTimeField.with(conversionTime))
            add(linesCountField.with(linesCount))
            add(filesCountField.with(filesCount))
            updateExternalUsages?.let { add(updateExternalUsagesField.with(it)) }
            add(pluginInfoField.with(getPluginInfoById(KotlinIdePlugin.id)))
        }
    )
}

enum class ConversionType(val text: String) {
    FILES("Files"), PSI_EXPRESSION("PSI_expression"), TEXT_EXPRESSION("Text_expression"), MCP("MCP");
}