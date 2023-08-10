// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.facet.isKpmModule

object KotlinASStatisticsEventFlags {
    private val LOG = Logger.getInstance(KotlinASStatisticsEventFlags::class.java)

    private val IS_KMP_MODULE = 0

    fun calculateAndPackEventsFlagsToLong(module: Module): Long = Builder().build {
        registerEvent(IS_KMP_MODULE, module.isKpmModule)
        // please register your events here
    }

    private class Builder {
        private val events = mutableMapOf<Int, Boolean>()

        fun build(f: Builder.() -> Unit): Long {
            f()
            return calculateLong()
        }

        /**
         * All the events should have different index in the range of [0..63]
         */
        fun registerEvent(eventIndex: Int, value: Boolean) {
            if (events[eventIndex] != null) {
                LOG.error("Event with index $eventIndex already registered")
                return
            }
            if (eventIndex < 0 || eventIndex > 63) {
                LOG.error("Event index should be in range [0, 63], but it is $eventIndex")
                return
            }

            events[eventIndex] = value
        }
        /**
         * You could register in this function events
         */
        private fun calculateLong(): Long {
            var flags = 0L
            fun setBit(bitIndex: Int) {
                flags = flags or (1L shl bitIndex)
            }
            for ((bitIndex, value) in events) {
                if (value) setBit(bitIndex)
            }
            return flags
        }

    }

}