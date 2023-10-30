// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import org.jetbrains.kotlin.idea.gradle.statistics.GradleStatisticsEventGroups


val kotlinBuildToolsFusEvenList = listOf(
    FusFlowSendingStep(
        GradleStatisticsEventGroups.All, KotlinBuildToolFusMetricName.entries, addIDEPluginVersion = true
    ),
    FusFlowSendingStep(
        GradleStatisticsEventGroups.Environment, listOf(
            KotlinBuildToolFusMetricName.CPU_NUMBER_OF_CORES,
            KotlinBuildToolFusMetricName.GRADLE_VERSION,
            KotlinBuildToolFusMetricName.ARTIFACTS_DOWNLOAD_SPEED,
            KotlinBuildToolFusMetricName.IDES_INSTALLED,
            KotlinBuildToolFusMetricName.EXECUTED_FROM_IDEA,
            KotlinBuildToolFusMetricName.PROJECT_PATH
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.Kapt, listOf(
            KotlinBuildToolFusMetricName.ENABLED_KAPT,
            KotlinBuildToolFusMetricName.ENABLED_DAGGER,
            KotlinBuildToolFusMetricName.ENABLED_DATABINDING
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.CompilerPlugins, listOf(
            KotlinBuildToolFusMetricName.ENABLED_COMPILER_PLUGIN_ALL_OPEN,
            KotlinBuildToolFusMetricName.ENABLED_COMPILER_PLUGIN_NO_ARG,
            KotlinBuildToolFusMetricName.ENABLED_COMPILER_PLUGIN_SAM_WITH_RECEIVER,
            KotlinBuildToolFusMetricName.JVM_DEFAULTS,
            KotlinBuildToolFusMetricName.USE_OLD_BACKEND
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.JS, listOf(
            KotlinBuildToolFusMetricName.JS_GENERATE_EXTERNALS,
            KotlinBuildToolFusMetricName.JS_GENERATE_EXECUTABLE_DEFAULT,
            KotlinBuildToolFusMetricName.JS_TARGET_MODE,
            KotlinBuildToolFusMetricName.JS_SOURCE_MAP,
            KotlinBuildToolFusMetricName.JS_PROPERTY_LAZY_INITIALIZATION,
            KotlinBuildToolFusMetricName.JS_OUTPUT_GRANULARITY,
            KotlinBuildToolFusMetricName.JS_IR_INCREMENTAL,
            KotlinBuildToolFusMetricName.WASM_IR_INCREMENTAL,
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.MPP, listOf(
            KotlinBuildToolFusMetricName.MPP_PLATFORMS,
            KotlinBuildToolFusMetricName.ENABLED_HMPP,
            KotlinBuildToolFusMetricName.JS_COMPILER_MODE
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.Libraries, listOf(
            KotlinBuildToolFusMetricName.LIBRARY_SPRING_VERSION,
            KotlinBuildToolFusMetricName.LIBRARY_VAADIN_VERSION,
            KotlinBuildToolFusMetricName.LIBRARY_GWT_VERSION,
            KotlinBuildToolFusMetricName.LIBRARY_HIBERNATE_VERSION
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.GradleConfiguration, listOf(
            KotlinBuildToolFusMetricName.GRADLE_DAEMON_HEAP_SIZE,
            KotlinBuildToolFusMetricName.GRADLE_BUILD_NUMBER_IN_CURRENT_DAEMON,
            KotlinBuildToolFusMetricName.CONFIGURATION_API_COUNT,
            KotlinBuildToolFusMetricName.CONFIGURATION_IMPLEMENTATION_COUNT,
            KotlinBuildToolFusMetricName.CONFIGURATION_COMPILE_COUNT,
            KotlinBuildToolFusMetricName.CONFIGURATION_COMPILE_ONLY_COUNT,
            KotlinBuildToolFusMetricName.CONFIGURATION_RUNTIME_COUNT,
            KotlinBuildToolFusMetricName.CONFIGURATION_RUNTIME_ONLY_COUNT,
            KotlinBuildToolFusMetricName.GRADLE_NUMBER_OF_TASKS,
            KotlinBuildToolFusMetricName.GRADLE_NUMBER_OF_UNCONFIGURED_TASKS,
            KotlinBuildToolFusMetricName.GRADLE_NUMBER_OF_INCREMENTAL_TASKS
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.ComponentVersions, listOf(
            KotlinBuildToolFusMetricName.KOTLIN_COMPILER_VERSION,
            KotlinBuildToolFusMetricName.KOTLIN_STDLIB_VERSION,
            KotlinBuildToolFusMetricName.KOTLIN_REFLECT_VERSION,
            KotlinBuildToolFusMetricName.KOTLIN_COROUTINES_VERSION,
            KotlinBuildToolFusMetricName.KOTLIN_SERIALIZATION_VERSION,
            KotlinBuildToolFusMetricName.ANDROID_GRADLE_PLUGIN_VERSION
        ), addIDEPluginVersion = true
    ),
    FusFlowSendingStep(
        GradleStatisticsEventGroups.KotlinFeatures, listOf(
            KotlinBuildToolFusMetricName.KOTLIN_LANGUAGE_VERSION,
            KotlinBuildToolFusMetricName.KOTLIN_API_VERSION,
            KotlinBuildToolFusMetricName.BUILD_SRC_EXISTS,
            KotlinBuildToolFusMetricName.BUILD_SRC_COUNT,
            KotlinBuildToolFusMetricName.GRADLE_BUILD_CACHE_USED,
            KotlinBuildToolFusMetricName.GRADLE_WORKER_API_USED,
            KotlinBuildToolFusMetricName.KOTLIN_OFFICIAL_CODESTYLE,
            KotlinBuildToolFusMetricName.KOTLIN_PROGRESSIVE_MODE,
            KotlinBuildToolFusMetricName.KOTLIN_KTS_USED
        )
    ),
    FusFlowSendingStep(
        GradleStatisticsEventGroups.GradlePerformance, listOf(
            KotlinBuildToolFusMetricName.GRADLE_BUILD_DURATION,
            KotlinBuildToolFusMetricName.GRADLE_EXECUTION_DURATION,
            KotlinBuildToolFusMetricName.NUMBER_OF_SUBPROJECTS,
            KotlinBuildToolFusMetricName.STATISTICS_VISIT_ALL_PROJECTS_OVERHEAD,
            KotlinBuildToolFusMetricName.STATISTICS_COLLECT_METRICS_OVERHEAD
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.UseScenarios, listOf(
            KotlinBuildToolFusMetricName.DEBUGGER_ENABLED,
            KotlinBuildToolFusMetricName.COMPILATION_STARTED,
            KotlinBuildToolFusMetricName.TESTS_EXECUTED,
            KotlinBuildToolFusMetricName.MAVEN_PUBLISH_EXECUTED,
            KotlinBuildToolFusMetricName.BUILD_FAILED
        )
    ),

    FusFlowSendingStep(
        GradleStatisticsEventGroups.BuildReports, listOf(
            KotlinBuildToolFusMetricName.SINGLE_FILE_BUILD_REPORT,
            KotlinBuildToolFusMetricName.FILE_BUILD_REPORT,
            KotlinBuildToolFusMetricName.HTTP_BUILD_REPORT,
            KotlinBuildToolFusMetricName.BUILD_SCAN_BUILD_REPORT
        )
    )
)
