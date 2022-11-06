/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.text.trimMiddle
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.NumericalMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics
import java.util.*
import kotlin.collections.HashMap

interface ICustomMetric

typealias NumericalMetricConsumer = (MetricsContainer /*current*/, MetricsContainer? /*previous*/) -> Long?
typealias BooleanMetricConsumer = (MetricsContainer /*current*/, MetricsContainer? /*previous*/) -> Boolean?
typealias StringMetricConsumer = (MetricsContainer /*current*/, MetricsContainer? /*previous*/) -> String?

enum class CustomNumericalMetrics(val eventGroup: GradleStatisticsEventGroups, val consumer: NumericalMetricConsumer) : ICustomMetric {
    TIME_BETWEEN_BUILDS(GradleStatisticsEventGroups.UseScenarios, { current, previous ->
        val finishTime = current.getMetric(NumericalMetrics.BUILD_FINISH_TIME)?.getValue()
        val prevFinishTime = previous?.getMetric(NumericalMetrics.BUILD_FINISH_TIME)?.getValue()
        if (finishTime != null && prevFinishTime != null) finishTime - prevFinishTime else null
    })
}

class KotlinGradleEvent(group: EventLogGroup, val eventName: GradleStatisticsEventGroups, vararg metrics: Any) {

    private val booleanEventFields = TreeMap<String, EventField<Boolean>>() // we prefer sorted collection for event creation
    private val booleanMetricConsumers = HashMap<String, BooleanMetricConsumer>()
    private val numericalEventFields = TreeMap<String, EventField<Long>>()
    private val numericalMetricConsumers = HashMap<String, NumericalMetricConsumer>()
    private val stringEventFields = TreeMap<String, EventField<String?>>()
    private val stringMetricConsumers = HashMap<String, StringMetricConsumer>()

    private fun allEventFieldsValues() = booleanEventFields.values.union(numericalEventFields.values).union(stringEventFields.values)

    val eventId: VarargEventId

    init {
        metrics.forEach {
            when (it) {
                is BooleanMetrics -> {
                    booleanEventFields[it.name] = EventFields.Boolean(it.name.lowercase())
                    booleanMetricConsumers[it.name] = { current, _ -> current.getMetric(it)?.getValue() }
                }
                is NumericalMetrics -> {
                    numericalEventFields[it.name] = EventFields.Long(it.name.lowercase())
                    numericalMetricConsumers[it.name] = { current, _ -> current.getMetric(it)?.getValue() }
                }
                is StringMetrics -> {
                    stringEventFields[it.name] = EventFields.StringValidatedByInlineRegexp(it.name.lowercase(), stringMetricRegexp(it))
                    stringMetricConsumers[it.name] = { current, _ ->
                        current.getMetric(it)?.getValue()?.anonymizeIdeString(it)
                    }
                }
                else -> throw IllegalArgumentException("$it is of unknown metric type.")
            }
        }
        CustomNumericalMetrics.values().forEach {
            if (it.eventGroup == eventName || eventName == GradleStatisticsEventGroups.All) {
                numericalEventFields[it.name] = EventFields.Long(it.name.lowercase())
                numericalMetricConsumers[it.name] = it.consumer
            }
        }
        eventId = if (addIDEPluginVersion(eventName))
            group.registerVarargEvent(eventName.name, EventFields.PluginInfo, *allEventFieldsValues().toTypedArray())
        else
            group.registerVarargEvent(eventName.name, *allEventFieldsValues().toTypedArray())
    }

    fun getEventPairs(currentMetrics: MetricsContainer, previousMetrics: MetricsContainer?): Array<EventPair<*>> {
        val booleanEventPairs = booleanEventFields.entries.mapNotNull {
            val value = booleanMetricConsumers[it.key]?.invoke(currentMetrics, previousMetrics)
            if (value != null)
                EventPair(it.value, value)
            else
                null
        }
        val numericalEventPairs = numericalEventFields.entries.mapNotNull {
            val value = numericalMetricConsumers[it.key]?.invoke(currentMetrics, previousMetrics)
            if (value != null)
                EventPair(it.value, value)
            else
                null
        }
        val stringEventPairs = stringEventFields.entries.mapNotNull {
            val value = stringMetricConsumers[it.key]?.invoke(currentMetrics, previousMetrics)
            if (value != null)
                EventPair(it.value, value)
            else
                null
        }
        return booleanEventPairs.union(numericalEventPairs).union(stringEventPairs).toTypedArray()
    }

    companion object {

        internal fun addIDEPluginVersion(event: GradleStatisticsEventGroups) = event == GradleStatisticsEventGroups.ComponentVersions
                || event == GradleStatisticsEventGroups.All


        private val IDE_STRING_ANONYMIZERS = lazy {
            mapOf(
                StringMetrics.PROJECT_PATH to { path: String ->
                    // This code duplicated logics of StatisticsUtil.getProjectId, which could not be directly reused:
                    // 1. the path of gradle project may not have corresponding project
                    // 2. the projectId should be stable and independent on IDE version
                    val presentableUrl = FileUtil.toSystemIndependentName(path)
                    val name =
                        PathUtilRt.getFileName(presentableUrl).lowercase(Locale.US).removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
                    val locationHash = Integer.toHexString((presentableUrl).hashCode())
                    val projectHash =
                        "${name.trimMiddle(name.length.coerceAtMost(254 - locationHash.length), useEllipsisSymbol = false)}.$locationHash"
                    @Suppress("DEPRECATION")
                    EventLogConfiguration.getInstance().anonymize(projectHash)
                })
        }

        private fun String.anonymizeIdeString(metric: StringMetrics) = if (metric.anonymization.anonymizeOnIdeSize())
            IDE_STRING_ANONYMIZERS.value[metric]?.invoke(this)
        else
            this

    }
}

// this function is needed for migration period. String metrics should contain own validation rules
private fun stringMetricRegexp(metric: StringMetrics): String =

    when (metric) {
        StringMetrics.LIBRARY_SPRING_VERSION,
        StringMetrics.LIBRARY_VAADIN_VERSION,
        StringMetrics.LIBRARY_GWT_VERSION,
        StringMetrics.LIBRARY_HIBERNATE_VERSION,
        StringMetrics.KOTLIN_COMPILER_VERSION,
        StringMetrics.KOTLIN_STDLIB_VERSION,
        StringMetrics.KOTLIN_REFLECT_VERSION,
        StringMetrics.KOTLIN_COROUTINES_VERSION,
        StringMetrics.KOTLIN_SERIALIZATION_VERSION,
        StringMetrics.ANDROID_GRADLE_PLUGIN_VERSION,
        StringMetrics.KOTLIN_LANGUAGE_VERSION,
        StringMetrics.KOTLIN_API_VERSION,
        StringMetrics.GRADLE_VERSION -> "(\\d+).(\\d+).(\\d+)-?(dev|snapshot|m\\d?|rc\\d?|beta\\d?)?"

        StringMetrics.JS_PROPERTY_LAZY_INITIALIZATION,
        StringMetrics.JS_GENERATE_EXECUTABLE_DEFAULT,
        StringMetrics.USE_OLD_BACKEND,
        StringMetrics.USE_FIR ->  "^((true|false)_?)+$"

        StringMetrics.JS_OUTPUT_GRANULARITY -> "(whole_program|per_module|per_file)"
        StringMetrics.JS_TARGET_MODE -> "^((both|browser|nodejs|none)_?)+$"
        StringMetrics.JVM_DEFAULTS -> "^((disable|enable|compatibility|all|all-compatibility)_?)+$"
        StringMetrics.PROJECT_PATH -> "([0-9A-Fa-f]{40,64})|undefined"
        StringMetrics.OS_TYPE -> "(Windows|Windows |Mac|Linux|FreeBSD|Solaris|Other|Mac OS X)\\d*"
        StringMetrics.IDES_INSTALLED -> "^((AS|OC|CL|IU|IC|WC)_?)+$"
        StringMetrics.MPP_PLATFORMS -> "^((common|metadata|jvm|js|arm32|arm64|mips32|mipsel32|x64|android|androidApp|androidNativeArm|androidNativeArm32|android_arm32|androidNativeArm64|android_arm64|androidNative|androidNativeX86|androidNativeX64|iosArm|iosArm32|ios_arm32|iosArm64|ios_arm64|ios|ios_x64|iosSim|iosX64|watchos|watchosArm32|watchosArm64|watchosX86|tvos|tvosArm64|tvosX64|linux|linuxArm32Hfp|linux_arm32_hfp|linuxMips32|linux_mips32|linuxMipsel32|linux_mipsel32|linuxX64|linux_x64|macos|osx|macosX64|macos_x64|mingw|mingwX64|mingw_x64|mingwX86|mingw_X86|wasm32|wasm)_?)+$"
        StringMetrics.JS_COMPILER_MODE -> "^((ir|legacy|both|UNKNOWN)_?)+$"

    }
