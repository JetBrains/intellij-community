// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.NumericalMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics

private const val BASE_FUS_VERSION = 11

internal object KotlinGradleFUSCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup(
        "kotlin.gradle.performance",
        BASE_FUS_VERSION + StringMetrics.VERSION + BooleanMetrics.VERSION + NumericalMetrics.VERSION
    )

    private fun listOfAllMetrics(): Array<Any> {
        val result = ArrayList<Any>()
        result.addAll(StringMetrics.values())
        result.addAll(BooleanMetrics.values())
        result.addAll(NumericalMetrics.values())
        return result.toArray()
    }

    private val kotlinGradleEvents = listOf(
        KotlinGradleEvent(GROUP, GradleStatisticsEventGroups.All, *listOfAllMetrics()),
        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.Environment,
            NumericalMetrics.CPU_NUMBER_OF_CORES,
            StringMetrics.GRADLE_VERSION,
            NumericalMetrics.ARTIFACTS_DOWNLOAD_SPEED,
            StringMetrics.IDES_INSTALLED,
            BooleanMetrics.EXECUTED_FROM_IDEA,
            StringMetrics.PROJECT_PATH
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.Kapt,
            BooleanMetrics.ENABLED_KAPT,
            BooleanMetrics.ENABLED_DAGGER,
            BooleanMetrics.ENABLED_DATABINDING
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.CompilerPlugins,
            BooleanMetrics.ENABLED_COMPILER_PLUGIN_ALL_OPEN,
            BooleanMetrics.ENABLED_COMPILER_PLUGIN_NO_ARG,
            BooleanMetrics.ENABLED_COMPILER_PLUGIN_SAM_WITH_RECEIVER,
            StringMetrics.JVM_DEFAULTS,
            StringMetrics.USE_OLD_BACKEND
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.JS,
            BooleanMetrics.JS_GENERATE_EXTERNALS,
            StringMetrics.JS_GENERATE_EXECUTABLE_DEFAULT,
            StringMetrics.JS_TARGET_MODE,
            BooleanMetrics.JS_SOURCE_MAP,
            StringMetrics.JS_PROPERTY_LAZY_INITIALIZATION,
            StringMetrics.JS_OUTPUT_GRANULARITY,
            BooleanMetrics.JS_KLIB_INCREMENTAL,
            BooleanMetrics.JS_IR_INCREMENTAL,
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.MPP,
            StringMetrics.MPP_PLATFORMS,
            BooleanMetrics.ENABLED_HMPP,
            StringMetrics.JS_COMPILER_MODE
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.Libraries,
            StringMetrics.LIBRARY_SPRING_VERSION,
            StringMetrics.LIBRARY_VAADIN_VERSION,
            StringMetrics.LIBRARY_GWT_VERSION,
            StringMetrics.LIBRARY_HIBERNATE_VERSION
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.GradleConfiguration,
            NumericalMetrics.GRADLE_DAEMON_HEAP_SIZE,
            NumericalMetrics.GRADLE_BUILD_NUMBER_IN_CURRENT_DAEMON,
            NumericalMetrics.CONFIGURATION_API_COUNT,
            NumericalMetrics.CONFIGURATION_IMPLEMENTATION_COUNT,
            NumericalMetrics.CONFIGURATION_COMPILE_COUNT,
            NumericalMetrics.CONFIGURATION_COMPILE_ONLY_COUNT,
            NumericalMetrics.CONFIGURATION_RUNTIME_COUNT,
            NumericalMetrics.CONFIGURATION_RUNTIME_ONLY_COUNT,
            NumericalMetrics.GRADLE_NUMBER_OF_TASKS,
            NumericalMetrics.GRADLE_NUMBER_OF_UNCONFIGURED_TASKS,
            NumericalMetrics.GRADLE_NUMBER_OF_INCREMENTAL_TASKS
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.ComponentVersions,
            StringMetrics.KOTLIN_COMPILER_VERSION,
            StringMetrics.KOTLIN_STDLIB_VERSION,
            StringMetrics.KOTLIN_REFLECT_VERSION,
            StringMetrics.KOTLIN_COROUTINES_VERSION,
            StringMetrics.KOTLIN_SERIALIZATION_VERSION,
            StringMetrics.ANDROID_GRADLE_PLUGIN_VERSION
        ),
        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.KotlinFeatures,
            StringMetrics.KOTLIN_LANGUAGE_VERSION,
            StringMetrics.KOTLIN_API_VERSION,
            BooleanMetrics.BUILD_SRC_EXISTS,
            NumericalMetrics.BUILD_SRC_COUNT,
            BooleanMetrics.GRADLE_BUILD_CACHE_USED,
            BooleanMetrics.GRADLE_WORKER_API_USED,
            BooleanMetrics.KOTLIN_OFFICIAL_CODESTYLE,
            BooleanMetrics.KOTLIN_PROGRESSIVE_MODE,
            BooleanMetrics.KOTLIN_KTS_USED
        ),
        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.GradlePerformance,
            NumericalMetrics.GRADLE_BUILD_DURATION,
            NumericalMetrics.GRADLE_EXECUTION_DURATION,
            NumericalMetrics.NUMBER_OF_SUBPROJECTS,
            NumericalMetrics.STATISTICS_VISIT_ALL_PROJECTS_OVERHEAD,
            NumericalMetrics.STATISTICS_COLLECT_METRICS_OVERHEAD
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.UseScenarios,
            BooleanMetrics.DEBUGGER_ENABLED,
            BooleanMetrics.COMPILATION_STARTED,
            BooleanMetrics.TESTS_EXECUTED,
            BooleanMetrics.MAVEN_PUBLISH_EXECUTED,
            BooleanMetrics.BUILD_FAILED
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.BuildReports,
            BooleanMetrics.SINGLE_FILE_BUILD_REPORT,
            BooleanMetrics.FILE_BUILD_REPORT,
            BooleanMetrics.HTTP_BUILD_REPORT,
            BooleanMetrics.BUILD_SCAN_BUILD_REPORT
        )
    )

    fun reportMetrics(currentMetrics: MetricsContainer, previousMetrics: MetricsContainer?) {
        kotlinGradleEvents.forEach { event ->
            val eventPairs = event.getEventPairs(currentMetrics, previousMetrics)
            if (eventPairs.isNotEmpty()) {
                if (KotlinGradleEvent.addIDEPluginVersion(event.eventName))
                    event.eventId.log(EventFields.PluginInfo.with(KotlinIdePlugin.getPluginInfo()), *eventPairs)
                else
                    event.eventId.log(*eventPairs)
            }
        }
    }
}

enum class GradleStatisticsEventGroups {
    All,
    Environment,
    Kapt,
    CompilerPlugins,
    MPP,
    JS,
    Libraries,
    GradleConfiguration,
    ComponentVersions,
    KotlinFeatures,
    GradlePerformance,
    UseScenarios,
    BuildReports
}

