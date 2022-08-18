// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

open class KotlinFUSLogger {
    companion object {
        private val context = FeatureUsageData().addData("plugin_version", KotlinIdePlugin.version)
        private val logger = FUCounterUsageLogger.getInstance()

        fun log(group: FUSEventGroups, event: String) {
            logger.logEvent(group.GROUP_ID, event, context)
        }

        fun log(group: FUSEventGroups, event: String, eventData: Map<String, String>) {
            val localContext = context.copy()
            for (entry in eventData) {
                localContext.addData(entry.key, entry.value)
            }
            logger.logEvent(group.GROUP_ID, event, localContext)
        }
    }
}