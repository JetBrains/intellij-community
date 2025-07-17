// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

//metric name and enum should be the same: KDoc + test, + version + test
enum class KotlinBuildToolFusMetricName(val metric: KotlinBuildToolFusMetric<*>) {
    BUILD_ID(BuildIdFusMetric()),

    //annotation processors
    EXECUTED_FROM_IDEA(KotlinBuildToolBooleanOverrideFusMetric("EXECUTED_FROM_IDEA")),
    ENABLED_KAPT(KotlinBuildToolBooleanFusMetric("ENABLED_KAPT")),
    ENABLED_DAGGER(KotlinBuildToolBooleanFusMetric("ENABLED_DAGGER")),
    ENABLED_DATABINDING(KotlinBuildToolBooleanFusMetric("ENABLED_DATABINDING")),
    ENABLED_KOVER(KotlinBuildToolBooleanFusMetric("ENABLED_KOVER")),

    ENABLED_COMPILER_PLUGIN_ALL_OPEN(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_ALL_OPEN")),
    ENABLED_COMPILER_PLUGIN_NO_ARG(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_NO_ARG")),
    ENABLED_COMPILER_PLUGIN_SAM_WITH_RECEIVER(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_SAM_WITH_RECEIVER")),
    ENABLED_COMPILER_PLUGIN_LOMBOK(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_LOMBOK")),
    ENABLED_COMPILER_PLUGIN_PARSELIZE(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_PARSELIZE")),
    ENABLED_COMPILER_PLUGIN_ATOMICFU(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_ATOMICFU")),
    ENABLED_COMPILER_PLUGIN_POWER_ASSERT(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_POWER_ASSERT")),
    ENABLED_COMPILER_PLUGIN_KOTLINX_KOVER(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_KOTLINX_KOVER")),
    ENABLED_COMPILER_PLUGIN_KOTLINX_SERIALIZATION(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_KOTLINX_SERIALIZATION")),
    ENABLED_COMPILER_PLUGIN_KOTLINX_DOKKA(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_KOTLINX_DOKKA")),
    ENABLED_COMPILER_PLUGIN_KOTLINX_BINARY_COMPATIBILITY_VALIDATOR(KotlinBuildToolBooleanFusMetric("ENABLED_COMPILER_PLUGIN_KOTLINX_BINARY_COMPATIBILITY_VALIDATOR")),
    ENABLED_HMPP(KotlinBuildToolBooleanOverrideFusMetric("ENABLED_HMPP")),

    // Enabled features
    BUILD_SRC_EXISTS(KotlinBuildToolBooleanFusMetric("BUILD_SRC_EXISTS")),
    BUILD_PREPARE_KOTLIN_BUILD_SCRIPT_MODEL(KotlinBuildToolBooleanFusMetric("BUILD_PREPARE_KOTLIN_BUILD_SCRIPT_MODEL")),
    GRADLE_BUILD_CACHE_USED(KotlinBuildToolBooleanOverrideFusMetric("GRADLE_BUILD_CACHE_USED")),
    GRADLE_WORKER_API_USED(KotlinBuildToolBooleanOverrideFusMetric("GRADLE_WORKER_API_USED")),
    GRADLE_CONFIGURATION_CACHE_ENABLED(KotlinBuildToolBooleanFusMetric("GRADLE_CONFIGURATION_CACHE_ENABLED")),
    GRADLE_PROJECT_ISOLATION_ENABLED(KotlinBuildToolBooleanFusMetric("GRADLE_PROJECT_ISOLATION_ENABLED")),

    KOTLIN_OFFICIAL_CODESTYLE(KotlinBuildToolBooleanOverrideFusMetric("KOTLIN_OFFICIAL_CODESTYLE")),
    KOTLIN_PROGRESSIVE_MODE(KotlinBuildToolBooleanOverrideFusMetric("KOTLIN_PROGRESSIVE_MODE")),
    KOTLIN_KTS_USED(KotlinBuildToolBooleanFusMetric("KOTLIN_KTS_USED")),
    KOTLIN_INCREMENTAL_NATIVE_ENABLED(KotlinBuildToolBooleanFusMetric("KOTLIN_INCREMENTAL_NATIVE_ENABLED")),

    JS_GENERATE_EXTERNALS(KotlinBuildToolBooleanFusMetric("JS_GENERATE_EXTERNALS")),

    JS_SOURCE_MAP(KotlinBuildToolBooleanFusMetric("JS_SOURCE_MAP")),

    JS_IR_INCREMENTAL(KotlinBuildToolBooleanFusMetric("JS_IR_INCREMENTAL")),

    WASM_IR_INCREMENTAL(KotlinBuildToolBooleanFusMetric("WASM_IR_INCREMENTAL")),
    //Garbage collector
    ENABLED_NOOP_GC(KotlinBuildToolBooleanFusMetric("ENABLED_NOOP_GC")),
    ENABLED_STWMS_GC(KotlinBuildToolBooleanFusMetric("ENABLED_STWMS_GC")),
    ENABLED_PMCS_GC(KotlinBuildToolBooleanFusMetric("ENABLED_PMCS_GC")),
    ENABLED_CMS_GC(KotlinBuildToolBooleanFusMetric("ENABLED_CMS_GC")),

    //Build reports
    FILE_BUILD_REPORT(KotlinBuildToolBooleanFusMetric("FILE_BUILD_REPORT")),
    BUILD_SCAN_BUILD_REPORT(KotlinBuildToolBooleanFusMetric("BUILD_SCAN_BUILD_REPORT")),
    HTTP_BUILD_REPORT(KotlinBuildToolBooleanFusMetric("HTTP_BUILD_REPORT")),
    SINGLE_FILE_BUILD_REPORT(KotlinBuildToolBooleanFusMetric("SINGLE_FILE_BUILD_REPORT")),
    JSON_BUILD_REPORT(KotlinBuildToolBooleanFusMetric("JSON_BUILD_REPORT")),

    //Dokka features
    ENABLED_DOKKA(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA")),
    ENABLED_DOKKA_HTML_TASK(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_HTML_TASK")),
    ENABLED_DOKKA_JAVADOC_TASK(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_JAVADOC_TASK")),
    ENABLED_DOKKA_GFM(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_GFM")),
    ENABLED_DOKKA_JEKYLL(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_JEKYLL")),
    ENABLED_DOKKA_HTML_MULTI_MODULE(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_HTML_MULTI_MODULE")),
    ENABLED_DOKKA_GFM_MULTI_MODULE(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_GFM_MULTI_MODULE")),
    ENABLED_DOKKA_JEKYLL_MULTI_MODULE(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_JEKYLL_MULTI_MODULE")),
    ENABLED_DOKKA_HTML_COLLECTOR(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_HTML_COLLECTOR")),
    ENABLED_DOKKA_JAVADOC_COLLECTOR(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_JAVADOC_COLLECTOR")),
    ENABLED_DOKKA_GFM_COLLECTOR(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_GFM_COLLECTOR")),
    ENABLED_DOKKA_JEKYLL_COLLECTOR(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_JEKYLL_COLLECTOR")),

    ENABLED_DOKKA_JAVADOC(KotlinBuildToolBooleanFusMetric("ENABLED_DOKKA_JAVADOC")),
    ENABLE_DOKKA_GENERATE_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_GENERATE_TASK")),
    ENABLE_DOKKA_GENERATE_HTML_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_GENERATE_HTML_TASK")),
    ENABLE_DOKKA_GENERATE_JAVADOC_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_GENERATE_JAVADOC_TASK")),
    ENABLE_DOKKA_GENERATE_PUBLICATION_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_GENERATE_PUBLICATION_TASK")),
    ENABLE_DOKKA_GENERATE_PUBLICATION_HTML_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_GENERATE_PUBLICATION_HTML_TASK")),
    ENABLE_DOKKA_GENERATE_PUBLICATION_JAVADOC_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_GENERATE_PUBLICATION_JAVADOC_TASK")),
    ENABLE_DOKKA_MODULE_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_MODULE_TASK")),
    ENABLE_DOKKA_MODULE_HTML_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_MODULE_HTML_TASK")),
    ENABLE_DOKKA_MODULE_JAVADOC_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_DOKKA_MODULE_JAVADOC_TASK")),
    ENABLE_LINK_DOKKA_GENERATE_TASK(KotlinBuildToolBooleanFusMetric("ENABLE_LINK_DOKKA_GENERATE_TASK")),

    // User scenarios
    DEBUGGER_ENABLED(KotlinBuildToolBooleanOverrideFusMetric("DEBUGGER_ENABLED")),
    COMPILATION_STARTED(KotlinBuildToolBooleanOverrideFusMetric("COMPILATION_STARTED")),
    TESTS_EXECUTED(KotlinBuildToolBooleanOverrideFusMetric("TESTS_EXECUTED")),
    MAVEN_PUBLISH_EXECUTED(KotlinBuildToolBooleanOverrideFusMetric("MAVEN_PUBLISH_EXECUTED")),
    BUILD_FAILED(KotlinBuildToolBooleanOverrideFusMetric("BUILD_FAILED")),
    KOTLIN_COMPILATION_FAILED(KotlinBuildToolBooleanFusMetric("KOTLIN_COMPILATION_FAILED")),

    // Other plugins enabled
    KOTLIN_JS_PLUGIN_ENABLED(KotlinBuildToolBooleanFusMetric("KOTLIN_JS_PLUGIN_ENABLED")),
    COCOAPODS_PLUGIN_ENABLED(KotlinBuildToolBooleanFusMetric("COCOAPODS_PLUGIN_ENABLED")),
    KOTLINX_KOVER_GRADLE_PLUGIN_ENABLED(KotlinBuildToolBooleanFusMetric("KOTLINX_KOVER_GRADLE_PLUGIN_ENABLED")),
    KOTLINX_SERIALIZATION_GRADLE_PLUGIN_ENABLED(KotlinBuildToolBooleanFusMetric("KOTLINX_SERIALIZATION_GRADLE_PLUGIN_ENABLED")),
    KOTLINX_ATOMICFU_GRADLE_PLUGIN_ENABLED(KotlinBuildToolBooleanFusMetric("KOTLINX_ATOMICFU_GRADLE_PLUGIN_ENABLED")),
    KOTLINX_BINARY_COMPATIBILITY_GRADLE_PLUGIN_ENABLED(KotlinBuildToolBooleanFusMetric("KOTLINX_BINARY_COMPATIBILITY_GRADLE_PLUGIN_ENABLED")),
    // User environment
    // Number of CPU cores. No other information (e.g. env.PROCESSOR_IDENTIFIER is not reported)
    CPU_NUMBER_OF_CORES(KotlinBuildToolLongOverrideFusMetric("CPU_NUMBER_OF_CORES")),

    //Download speed in Bytes per second
    ARTIFACTS_DOWNLOAD_SPEED(AnonymizedKotlinBuildToolFusMetric(KotlinBuildToolLongOverrideFusMetric("ARTIFACTS_DOWNLOAD_SPEED"), Long::random10)),

    // Build script
    GRADLE_DAEMON_HEAP_SIZE(AnonymizedKotlinBuildToolFusMetric(KotlinBuildToolLongOverrideFusMetric("GRADLE_DAEMON_HEAP_SIZE"), Long::random10)),

    GRADLE_BUILD_NUMBER_IN_CURRENT_DAEMON(KotlinBuildToolLongOverrideFusMetric("GRADLE_BUILD_NUMBER_IN_CURRENT_DAEMON")),

    // gradle configuration types
    CONFIGURATION_API_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("CONFIGURATION_API_COUNT")),
    CONFIGURATION_IMPLEMENTATION_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("CONFIGURATION_IMPLEMENTATION_COUNT")),
    CONFIGURATION_COMPILE_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("CONFIGURATION_COMPILE_COUNT")),
    CONFIGURATION_COMPILE_ONLY_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("CONFIGURATION_COMPILE_ONLY_COUNT")),
    CONFIGURATION_RUNTIME_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("CONFIGURATION_RUNTIME_COUNT")),
    CONFIGURATION_RUNTIME_ONLY_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("CONFIGURATION_RUNTIME_ONLY_COUNT")),

    // gradle task types
    GRADLE_NUMBER_OF_TASKS(KotlinBuildToolLongSumAndRandomFusMetric("GRADLE_NUMBER_OF_TASKS")),
    GRADLE_NUMBER_OF_UNCONFIGURED_TASKS(KotlinBuildToolLongSumAndRandomFusMetric("GRADLE_NUMBER_OF_UNCONFIGURED_TASKS")),
    GRADLE_NUMBER_OF_INCREMENTAL_TASKS(KotlinBuildToolLongSumAndRandomFusMetric("GRADLE_NUMBER_OF_INCREMENTAL_TASKS")),

    //Features
    BUILD_SRC_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("BUILD_SRC_COUNT")),

    // Build performance
    // duration of the whole gradle build
    GRADLE_BUILD_DURATION(KotlinBuildToolLongOverrideFusMetric("GRADLE_BUILD_DURATION")),

    //duration of the execution gradle phase
    GRADLE_EXECUTION_DURATION(KotlinBuildToolLongOverrideFusMetric("GRADLE_EXECUTION_DURATION")),

    //performance of compiler
    COMPILATIONS_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("COMPILATIONS_COUNT")),
    INCREMENTAL_COMPILATIONS_COUNT(KotlinBuildToolLongSumAndRandomFusMetric("INCREMENTAL_COMPILATIONS_COUNT")),
    COMPILATION_DURATION(KotlinBuildToolLongSumFusMetric("COMPILATION_DURATION")),
    COMPILED_LINES_OF_CODE(KotlinBuildToolLongSumAndRandomFusMetric("COMPILED_LINES_OF_CODE")),
    COMPILATION_LINES_PER_SECOND(KotlinBuildToolLongOverrideFusMetric("COMPILATION_LINES_PER_SECOND")),
    ANALYSIS_LINES_PER_SECOND(KotlinBuildToolLongAverageFusMetric("ANALYSIS_LINES_PER_SECOND")),
    CODE_GENERATION_LINES_PER_SECOND(KotlinBuildToolLongAverageFusMetric("CODE_GENERATION_LINES_PER_SECOND")),

    //only Kotlin subprojects are counted
    NUMBER_OF_SUBPROJECTS(KotlinBuildToolLongSumAndRandomFusMetric("NUMBER_OF_SUBPROJECTS")),

    STATISTICS_VISIT_ALL_PROJECTS_OVERHEAD(KotlinBuildToolLongSumAndRandomFusMetric("STATISTICS_VISIT_ALL_PROJECTS_OVERHEAD")),
    STATISTICS_COLLECT_METRICS_OVERHEAD(KotlinBuildToolLongSumAndRandomFusMetric("STATISTICS_COLLECT_METRICS_OVERHEAD")),

    // User scenarios

    // this value is not reported, only time intervals from the previous build are used
    BUILD_FINISH_TIME(KotlinBuildToolLongSumFusMetric("BUILD_FINISH_TIME")),
    // User environment
    GRADLE_VERSION(VersionStringFusMetric("GRADLE_VERSION")),
    PROJECT_PATH(PathFusMetric("PROJECT_PATH")),

    OS_TYPE(OverrideRegexStringFusMetric("OS_TYPE","(Windows|Windows |Windows Server |Mac|Linux|FreeBSD|Solaris|Other|Mac OS X)\\d*")),

    IDES_INSTALLED(ConcatenatedAllowedListValuesStringFusMetric("IDES_INSTALLED",listOf("AS", "OC", "CL", "IU", "IC", "WC"))),

    // Build script
    MPP_PLATFORMS(
        ConcatenatedAllowedListValuesStringFusMetric("MPP_PLATFORMS",
    listOf(
    "common",
    "native",
    "jvm",
    "js",
    "android_x64",
    "android_x86",
    "androidJvm",
    "android_arm32",
    "android_arm64",
    "ios_arm64",
    "ios_simulator_arm64",
    "ios_x64",
    "watchos_arm32",
    "watchos_arm64",
    "watchos_x64",
    "watchos_simulator_arm64",
    "watchos_device_arm64",
    "tvos_arm64",
    "tvos_x64",
    "tvos_simulator_arm64",
    "linux_arm32_hfp",
    "linux_arm64",
    "linux_x64",
    "macos_x64",
    "macos_arm64",
    "mingw_x64",
    "wasm"
    )
    )
    ),
    JS_COMPILER_MODE(ConcatenatedAllowedListValuesStringFusMetric("JS_COMPILER_MODE", listOf("ir", "legacy", "both", "UNKNOWN"))),

    // Component versions
    LIBRARY_SPRING_VERSION(IgnoreDefaultVersionStringFusMetric("LIBRARY_SPRING_VERSION")),
    LIBRARY_VAADIN_VERSION(IgnoreDefaultVersionStringFusMetric("LIBRARY_VAADIN_VERSION")),
    LIBRARY_GWT_VERSION(IgnoreDefaultVersionStringFusMetric("LIBRARY_GWT_VERSION")),
    LIBRARY_HIBERNATE_VERSION(IgnoreDefaultVersionStringFusMetric("LIBRARY_HIBERNATE_VERSION")),

    KOTLIN_COMPILER_VERSION(VersionStringFusMetric("KOTLIN_COMPILER_VERSION")),
    KOTLIN_STDLIB_VERSION( VersionStringFusMetric("KOTLIN_STDLIB_VERSION")),
    KOTLIN_REFLECT_VERSION(VersionStringFusMetric("KOTLIN_REFLECT_VERSION")),
    KOTLIN_COROUTINES_VERSION(VersionStringFusMetric("KOTLIN_COROUTINES_VERSION")),
    KOTLIN_SERIALIZATION_VERSION(VersionStringFusMetric("KOTLIN_SERIALIZATION_VERSION")),

    ANDROID_GRADLE_PLUGIN_VERSION(VersionStringFusMetric("ANDROID_GRADLE_PLUGIN_VERSION")),

    // Features
    KOTLIN_LANGUAGE_VERSION(VersionStringFusMetric("KOTLIN_LANGUAGE_VERSION")),
    KOTLIN_API_VERSION(VersionStringFusMetric("KOTLIN_API_VERSION")),
    JS_GENERATE_EXECUTABLE_DEFAULT(ConcatenatedAllowedListValuesStringFusMetric("JS_GENERATE_EXECUTABLE_DEFAULT", listOf("true", "false"))),
    JS_TARGET_MODE(ConcatenatedAllowedListValuesStringFusMetric("JS_TARGET_MODE", listOf("both", "browser", "nodejs", "none"))),
    JS_OUTPUT_GRANULARITY(OverrideRegexStringFusMetric("JS_OUTPUT_GRANULARITY", "(whole_program|per_module|per_file)")),

    // Compiler parameters
    JVM_DEFAULTS(ConcatenatedAllowedListValuesStringFusMetric("JVM_DEFAULTS", listOf("enable", "no-compatibility", "disable"))),
    USE_OLD_BACKEND(ConcatenatedAllowedListValuesStringFusMetric("USE_OLD_BACKEND", listOf("true", "false"))),
    USE_FIR(ConcatenatedAllowedListValuesStringFusMetric("USE_FIR", listOf("true", "false"))),

    JS_PROPERTY_LAZY_INITIALIZATION(ConcatenatedAllowedListValuesStringFusMetric("JS_PROPERTY_LAZY_INITIALIZATION", listOf("true", "false")));

}

internal val kotlinFusMetricsMap = KotlinBuildToolFusMetricName.entries.associateBy { it.metric.metricRawName }


