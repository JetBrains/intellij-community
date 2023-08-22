// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

object J2KFusCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup("kotlin.ide.j2k", 2)

    private val sourceType = EventFields.String("source_type", ConversionType.values().map { it.text })
    private val isNewJ2K = EventFields.Boolean("is_new_j2k")
    private val conversionTime = EventFields.Long("conversion_time")
    private val linesCount = EventFields.Int("lines_count")
    private val filesCount = EventFields.Int("files_count")
    private val pluginInfo = EventFields.PluginInfo

    private val event = GROUP.registerVarargEvent(
        "Conversion",
        sourceType,
        isNewJ2K,
        conversionTime,
        linesCount,
        filesCount,
        pluginInfo,
    )

    fun log(
        type: ConversionType,
        isNewJ2k: Boolean,
        conversionTime: Long,
        linesCount: Int,
        filesCount: Int
    ) = event.log(
        this.sourceType.with(type.text),
        this.isNewJ2K.with(isNewJ2k),
        this.conversionTime.with(conversionTime),
        this.linesCount.with(linesCount),
        this.filesCount.with(filesCount),
        this.pluginInfo.with(getPluginInfoById(KotlinIdePlugin.id)),
    )
}

enum class ConversionType(val text: String) {
    FILES("Files"), PSI_EXPRESSION("PSI_expression"), TEXT_EXPRESSION("Text_expression");
}