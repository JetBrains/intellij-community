// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.NumericalMetrics
import org.jetbrains.kotlin.statistics.metrics.StringListMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics

private const val BASE_FUS_VERSION = 20

object KotlinGradleFUSCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup(
        "kotlin.gradle.performance",
        BASE_FUS_VERSION + StringMetrics.VERSION + BooleanMetrics.VERSION + NumericalMetrics.VERSION + StringListMetrics.VERSION,
    )

    private fun listOfAllMetrics(): Array<Any> {
        val result = ArrayList<Any>()
        result.addAll(StringMetrics.entries.toTypedArray())
        result.addAll(BooleanMetrics.entries.toTypedArray())
        result.addAll(NumericalMetrics.entries.toTypedArray())
        result.addAll(StringListMetrics.entries.toTypedArray())
        return result.toArray()
    }

    private val kotlinGradleEvents = listOf(
        KotlinGradleEvent(GROUP, GradleStatisticsEventGroups.All, *listOfAllMetrics()),
        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.Environment,
            NumericalMetrics.CPU_NUMBER_OF_CORES,
            StringMetrics.GRADLE_VERSION,
            NumericalMetrics.ARTIFACTS_DOWNLOAD_SPEED,
            StringListMetrics.IDES_INSTALLED,
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
            BooleanMetrics.ENABLED_COMPILER_REFERENCE_INDEX,
            StringListMetrics.JVM_DEFAULTS,
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.JS,
            StringListMetrics.JS_TARGET_MODE,
            BooleanMetrics.JS_SOURCE_MAP,
            StringListMetrics.JS_PROPERTY_LAZY_INITIALIZATION,
            StringMetrics.JS_OUTPUT_GRANULARITY,
            BooleanMetrics.JS_IR_INCREMENTAL,
            BooleanMetrics.WASM_IR_INCREMENTAL,
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.MPP,
            StringListMetrics.MPP_PLATFORMS,
            BooleanMetrics.ENABLED_HMPP,
            StringListMetrics.JS_COMPILER_MODE,
            BooleanMetrics.KOTLIN_CROSS_COMPILATION_DISABLED,
            BooleanMetrics.KOTLIN_CROSS_COMPILATION_NOT_SUPPORTED,
            BooleanMetrics.KOTLIN_NATIVE_CACHE_DISABLED,
            StringListMetrics.WASM_COMPILER_MODE,
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
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.ComponentVersions,
            StringMetrics.KOTLIN_COMPILER_VERSION,
            StringMetrics.KOTLIN_STDLIB_VERSION,
            StringMetrics.KOTLIN_REFLECT_VERSION,
            StringMetrics.KOTLIN_COROUTINES_VERSION,
            StringMetrics.KOTLIN_SERIALIZATION_VERSION,
            StringMetrics.ANDROID_GRADLE_PLUGIN_VERSION,
            StringMetrics.KOTLIN_GRADLE_PLUGIN_VERSION,
        ),
        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.KotlinFeatures,
            StringMetrics.KOTLIN_LANGUAGE_VERSION,
            StringMetrics.KOTLIN_API_VERSION,
            BooleanMetrics.BUILD_SRC_EXISTS,
            NumericalMetrics.BUILD_SRC_COUNT,
            BooleanMetrics.GRADLE_BUILD_CACHE_USED,
            BooleanMetrics.GRADLE_WORKER_API_USED,
            BooleanMetrics.KOTLIN_PROGRESSIVE_MODE,
            BooleanMetrics.KOTLIN_KTS_USED,
            BooleanMetrics.KOTLIN_BTA_USED,
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
        ),

        KotlinGradleEvent(
            GROUP, GradleStatisticsEventGroups.CompilerArguments,
            BooleanMetrics.CLI_DEBUG_LEVEL_COMPILER_CHECKS_ENABLED,
            BooleanMetrics.CLI_LENIENT_MODE_ENABLED,
            BooleanMetrics.CLI_ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS,
            BooleanMetrics.CLI_ALLOW_CONDITION_IMPLIES_RETURNS_CONTRACTS,
            BooleanMetrics.CLI_ALLOW_CONTRACTS_ON_MORE_FUNCTIONS,
            BooleanMetrics.CLI_ALLOW_HOLDSIN_CONTRACT,
            BooleanMetrics.CLI_ALLOW_KOTLIN_PACKAGE,
            BooleanMetrics.CLI_ALLOW_PRE_17_RUNTIME_JDK,
            BooleanMetrics.CLI_ALLOW_REIFIED_TYPE_IN_CATCH,
            BooleanMetrics.CLI_ALLOW_RETURNS_RESULT_OF,
            BooleanMetrics.CLI_ANNOTATION_TARGET_ALL_ENABLED,
            BooleanMetrics.CLI_CALLABLE_REFERENCES_TO_CONTEXTUAL_ENABLED,
            BooleanMetrics.CLI_CHECK_PHASE_CONDITIONS_ENABLED,
            BooleanMetrics.CLI_COLLECTION_LITERALS_ENABLED,
            BooleanMetrics.CLI_COMPANION_BLOCKS_ENABLED,
            BooleanMetrics.CLI_COMPANION_BLOCKS_AND_EXTENSIONS_ENABLED,
            BooleanMetrics.CLI_CONSISTENT_DATA_CLASS_COPY_VISIBILITY_ENABLED,
            BooleanMetrics.CLI_CONTEXT_PARAMETERS_ENABLED,
            BooleanMetrics.CLI_CONTEXT_SENSITIVE_RESOLUTION_ENABLED,
            BooleanMetrics.CLI_DATA_FLOW_BASED_EXHAUSTIVENESS_ENABLED,
            BooleanMetrics.CLI_DETAILED_PERF_ENABLED,
            BooleanMetrics.CLI_DEFAULT_SCRIPTING_PLUGIN_DISABLED,
            BooleanMetrics.CLI_SOURCE_FILES_SORTING_DISABLED,
            BooleanMetrics.CLI_WARN_ON_ERROR_SUPPRESSION_DISABLED,
            BooleanMetrics.CLI_EAGER_LAMBDA_ANALYSIS_ENABLED,
            BooleanMetrics.CLI_COMPILER_INCREMENTAL_COMPILATION_ENABLED,
            BooleanMetrics.CLI_ALLOW_EXPECT_ACTUAL_CLASSES,
            BooleanMetrics.CLI_EXPLICIT_BACKING_FIELDS_ENABLED,
            BooleanMetrics.CLI_EXPLICIT_CONTEXT_ARGUMENTS_ENABLED,
            BooleanMetrics.CLI_FIR_AGGRESSIVE_PRUNING_ENABLED,
            BooleanMetrics.CLI_HEADER_MODE_ENABLED,
            BooleanMetrics.CLI_INTRINSIC_CONST_EVALUATION_ENABLED,
            BooleanMetrics.CLI_LIST_PHASES_ENABLED,
            BooleanMetrics.CLI_LOCAL_TYPE_ALIASES_ENABLED,
            BooleanMetrics.CLI_METADATA_KLIB_ENABLED,
            BooleanMetrics.CLI_MULTI_DOLLAR_INTERPOLATION_ENABLED,
            BooleanMetrics.CLI_MULTI_PLATFORM_ENABLED,
            BooleanMetrics.CLI_NESTED_TYPE_ALIASES_ENABLED,
            BooleanMetrics.CLI_INLINING_DISABLED,
            BooleanMetrics.CLI_NON_LOCAL_BREAK_CONTINUE_ENABLED,
            BooleanMetrics.CLI_PRINT_CONFIGURATION_ENABLED,
            BooleanMetrics.CLI_PROFILE_PHASES_ENABLED,
            BooleanMetrics.CLI_RENDER_INTERNAL_DIAGNOSTIC_NAMES_ENABLED,
            BooleanMetrics.CLI_REPORT_ALL_WARNINGS_ENABLED,
            BooleanMetrics.CLI_REPORT_OUTPUT_FILES_ENABLED,
            BooleanMetrics.CLI_REPORT_PERF_ENABLED,
            BooleanMetrics.CLI_SKIP_METADATA_VERSION_CHECK_ENABLED,
            BooleanMetrics.CLI_SKIP_PRERELEASE_CHECK_ENABLED,
            BooleanMetrics.CLI_STDLIB_COMPILATION_ENABLED,
            BooleanMetrics.CLI_VERSION_WARNINGS_SUPPRESSED,
            BooleanMetrics.CLI_FIR_IC_ENABLED,
            BooleanMetrics.CLI_FIR_LIGHT_TREE_PARSER_ENABLED,
            BooleanMetrics.CLI_WHEN_GUARDS_ENABLED,
            BooleanMetrics.CLI_SCRIPT_EVALUATION_ENABLED,
            BooleanMetrics.CLI_ALLOW_NO_SOURCE_FILES,
            BooleanMetrics.CLI_ALLOW_UNSTABLE_DEPENDENCIES,
            BooleanMetrics.CLI_ANNOTATIONS_IN_METADATA_ENABLED,
            BooleanMetrics.CLI_JVM_DEBUG_MODE_ENABLED,
            BooleanMetrics.CLI_DIRECT_JAVA_ACTUALIZATION_ENABLED,
            BooleanMetrics.CLI_STANDARD_SCRIPTING_DISABLED,
            BooleanMetrics.CLI_JVM_TYPE_ANNOTATIONS_EMITTED,
            BooleanMetrics.CLI_ENHANCED_COROUTINES_DEBUGGING_ENABLED,
            BooleanMetrics.CLI_STRICT_METADATA_VERSION_SEMANTICS_ENABLED,
            BooleanMetrics.CLI_INDY_ANNOTATED_LAMBDAS_ALLOWED,
            BooleanMetrics.CLI_JAVA_DIRECT_ENABLED,
            BooleanMetrics.CLI_VALHALLA_VALUE_CLASSES_ENABLED,
            BooleanMetrics.CLI_JVM_PREVIEW_FEATURES_ENABLED,
            BooleanMetrics.CLI_JVM_EXPOSE_BOXED_ENABLED,
            BooleanMetrics.CLI_MULTIFILE_PARTS_INHERITANCE_ENABLED,
            BooleanMetrics.CLI_CALL_ASSERTIONS_DISABLED,
            BooleanMetrics.CLI_NEW_JAVA_ANNOTATION_TARGETS_DISABLED,
            BooleanMetrics.CLI_OPTIMIZATIONS_DISABLED,
            BooleanMetrics.CLI_PARAM_ASSERTIONS_DISABLED,
            BooleanMetrics.CLI_RECEIVER_ASSERTIONS_DISABLED,
            BooleanMetrics.CLI_JAR_TIMESTAMPS_RESET_DISABLED,
            BooleanMetrics.CLI_SOURCE_DEBUG_EXTENSION_DISABLED,
            BooleanMetrics.CLI_UNIFIED_NULL_CHECKS_DISABLED,
            BooleanMetrics.CLI_OUTPUT_BUILTINS_METADATA_ENABLED,
            BooleanMetrics.CLI_SANITIZE_PARENTHESES_ENABLED,
            BooleanMetrics.CLI_MISSING_BUILTINS_ERROR_SUPPRESSED,
            BooleanMetrics.CLI_OLD_INLINE_CLASSES_MANGLING_SCHEME_USED,
            BooleanMetrics.CLI_FAST_JAR_FILE_SYSTEM_USED,
            BooleanMetrics.CLI_INLINE_SCOPES_NUMBERS_USED,
            BooleanMetrics.CLI_METADATA_ON_INCREMENTAL_CLASSPATH_USED,
            BooleanMetrics.CLI_OLD_CLASS_FILES_READING_USED,
            BooleanMetrics.CLI_TYPE_TABLE_USED,
            BooleanMetrics.CLI_BYTECODE_VALIDATION_ENABLED,
            BooleanMetrics.CLI_KOTLIN_RUNTIME_INCLUDED_IN_JAR,
            BooleanMetrics.CLI_JAVA_PARAMETERS_METADATA_ENABLED,
            BooleanMetrics.CLI_JDK_EXCLUDED_FROM_CLASSPATH,
            BooleanMetrics.CLI_KOTLIN_REFLECT_EXCLUDED_FROM_CLASSPATH,
            BooleanMetrics.CLI_KOTLIN_STDLIB_EXCLUDED_FROM_CLASSPATH,
            BooleanMetrics.CLI_KLIB_SIGNATURE_CLASH_CHECKS_ENABLED,
            BooleanMetrics.CLI_LIBRARY_SPECIAL_COMPATIBILITY_CHECKS_SKIPPED,
            BooleanMetrics.CLI_LEGACY_METADATA_JAR_ENABLED,
            BooleanMetrics.CLI_FRIEND_MODULES_DISABLED,
            BooleanMetrics.CLI_IR_DCE_ENABLED,
            BooleanMetrics.CLI_IR_DCE_REACHABILITY_INFO_PRINTED,
            BooleanMetrics.CLI_IR_PRODUCE_JS_ENABLED,
            BooleanMetrics.CLI_IR_PRODUCE_KLIB_DIR_ENABLED,
            BooleanMetrics.CLI_IR_PRODUCE_KLIB_FILE_ENABLED,
            BooleanMetrics.CLI_STRICT_IMPLICIT_EXPORT_TYPES_ENABLED,
            BooleanMetrics.CLI_NOPACK_ENABLED,
            BooleanMetrics.CLI_WASM_BACKEND_EXPLICITLY_SELECTED,
            BooleanMetrics.CLI_WASM_IC_REGENERATE_UNCHANGED_MODULES_ENABLED,
            BooleanMetrics.CLI_WASM_DEBUG_FRIENDLY_COMPILATION_ENABLED,
            BooleanMetrics.CLI_WASM_DEBUG_INFO_ENABLED,
            BooleanMetrics.CLI_WASM_DEBUGGER_CUSTOM_FORMATTERS_ENABLED,
            BooleanMetrics.CLI_WASM_ARRAY_RANGE_CHECKS_SAFE_ELIMINATION_DISABLED,
            BooleanMetrics.CLI_WASM_ARRAY_RANGE_CHECKS_ENABLED,
            BooleanMetrics.CLI_WASM_ASSERTS_ENABLED,
            BooleanMetrics.CLI_WASM_TAIL_CALLS_ENABLED,
            BooleanMetrics.CLI_WASM_CLOSED_WORLD_MULTIMODULE_ENABLED,
            BooleanMetrics.CLI_WASM_DWARF_GENERATED,
            BooleanMetrics.CLI_WASM_WAT_GENERATED,
            BooleanMetrics.CLI_WASM_INCLUDED_MODULE_ONLY_ENABLED,
            BooleanMetrics.CLI_WASM_KCLASS_FQN_SUPPORT_ENABLED,
            BooleanMetrics.CLI_WASM_JSTAG_DISABLED,
            BooleanMetrics.CLI_WASM_SOURCE_MAP_UNAVAILABLE_SOURCES_INCLUDED,
            BooleanMetrics.CLI_WASM_NEW_EXCEPTION_PROPOSAL_USED,
            BooleanMetrics.CLI_WASM_STACK_SWITCHING_PROPOSAL_USED,
            BooleanMetrics.CLI_WASM_TRAPS_INSTEAD_OF_EXCEPTIONS_USED,
            BooleanMetrics.CLI_JS_DTS_USE_UNKNOWN_INSTEAD_ANY_ENABLED,
            BooleanMetrics.CLI_JS_EXTENSION_FUNCTIONS_IN_EXTERNALS_ENABLED,
            BooleanMetrics.CLI_JS_IMPLEMENTABLE_INTERFACES_EXPORTING_ENABLED,
            BooleanMetrics.CLI_JS_SUSPEND_FUNCTION_EXPORTING_ENABLED,
            BooleanMetrics.CLI_JS_ES_ARROW_FUNCTIONS_USED,
            BooleanMetrics.CLI_JS_ES_CLASSES_USED,
            BooleanMetrics.CLI_JS_ES_GENERATORS_USED,
            BooleanMetrics.CLI_ENABLED_EXPORT_KDOC,
            BooleanMetrics.CLI_JS_POLYFILLS_GENERATED,
            BooleanMetrics.CLI_JS_INTEGER_DIVISION_CHECK_ENABLED,
            BooleanMetrics.CLI_JS_IR_BUILD_CACHE_ENABLED,
            BooleanMetrics.CLI_JS_IR_GENERATE_INLINE_ANONYMOUS_FUNCTIONS_ENABLED,
            BooleanMetrics.CLI_JS_IR_MINIMIZED_MEMBER_NAMES_ENABLED,
            BooleanMetrics.CLI_JS_IR_PER_FILE_OUTPUT_ENABLED,
            BooleanMetrics.CLI_JS_IR_SAFE_EXTERNAL_BOOLEAN_ENABLED,
            BooleanMetrics.CLI_JS_GENERATED_CODE_OPTIMIZATION_ENABLED,
            BooleanMetrics.CLI_JS_SUSPEND_LAMBDA_EXPORTING_ENABLED,
            BooleanMetrics.CLI_NATIVE_CHECK_DEPENDENCIES_ENABLED,
            BooleanMetrics.CLI_NATIVE_CHECK_EXTERNAL_CALLS_ENABLED,
            BooleanMetrics.CLI_NATIVE_PER_FILE_CACHE_FORCED,
            BooleanMetrics.CLI_NATIVE_OBJC_GENERICS_DISABLED,
            BooleanMetrics.CLI_NATIVE_FRAMEWORK_BINARY_OMITTED,
            BooleanMetrics.CLI_NATIVE_PRINT_BITCODE_ENABLED,
            BooleanMetrics.CLI_NATIVE_PRINT_FILES_ENABLED,
            BooleanMetrics.CLI_NATIVE_PRINT_IR_ENABLED,
            BooleanMetrics.CLI_NATIVE_STATIC_FRAMEWORK_ENABLED,
            BooleanMetrics.CLI_NATIVE_VERIFY_BITCODE_ENABLED,
            BooleanMetrics.CLI_NATIVE_RUNTIME_ASSERTIONS_ENABLED,
            BooleanMetrics.CLI_NATIVE_DEBUG_INFO_ENABLED,
            BooleanMetrics.CLI_NATIVE_NO_EXIT_TEST_RUNNER_GENERATED,
            BooleanMetrics.CLI_NATIVE_TEST_RUNNER_GENERATED,
            BooleanMetrics.CLI_NATIVE_WORKER_TEST_RUNNER_GENERATED,
            BooleanMetrics.CLI_NATIVE_LIST_TARGETS_ENABLED,
            BooleanMetrics.CLI_NATIVE_DEFAULT_LIBS_EXCLUDED,
            BooleanMetrics.CLI_NATIVE_MAIN_ENTRY_POINT_EXTERNAL,
            BooleanMetrics.CLI_NATIVE_STDLIB_EXCLUDED,
            BooleanMetrics.CLI_NATIVE_OPTIMIZATION_ENABLED,
            NumericalMetrics.CLI_BACKEND_THREADS_COUNT,
            NumericalMetrics.CLI_KLIB_ZIP_FILE_ACCESSOR_CACHE_LIMIT,
            StringMetrics.CLI_EXPLICIT_RETURN_TYPES_MODE,
            StringMetrics.CLI_ANNOTATION_DEFAULT_TARGET,
            StringMetrics.CLI_EXPLICIT_API_MODE,
            StringMetrics.CLI_HEADER_MODE_TYPE,
            StringMetrics.CLI_METADATA_VERSION,
            StringMetrics.CLI_NAME_BASED_DESTRUCTURING_MODE,
            StringMetrics.CLI_RETURN_VALUE_CHECKER_MODE,
            StringMetrics.CLI_VERIFY_IR_MODE,
            StringMetrics.CLI_ABI_STABILITY_MODE,
            StringMetrics.CLI_ASSERTIONS_MODE,
            StringMetrics.CLI_JDK_RELEASE_VERSION,
            StringMetrics.CLI_JSPECIFY_ANNOTATIONS_MODE,
            StringMetrics.CLI_LAMBDAS_CODEGEN_MODE,
            StringMetrics.CLI_SAM_CONVERSIONS_CODEGEN_MODE,
            StringMetrics.CLI_STRING_CONCAT_CODEGEN_MODE,
            StringMetrics.CLI_COMPATQUAL_CHECKER_FRAMEWORK_ANNOTATIONS_MODE,
            StringMetrics.CLI_WHEN_EXPRESSIONS_CODEGEN_MODE,
            StringMetrics.CLI_JVM_TARGET_VERSION,
            StringMetrics.CLI_KLIB_ABI_VERSION,
            StringMetrics.CLI_KLIB_DUPLICATED_UNIQUE_NAME_STRATEGY,
            StringMetrics.CLI_KLIB_IR_INLINER_MODE,
            StringMetrics.CLI_PARTIAL_LINKAGE_MODE,
            StringMetrics.CLI_PARTIAL_LINKAGE_LOG_LEVEL,
            StringMetrics.CLI_IR_DCE_RUNTIME_DIAGNOSTIC_MODE,
            StringMetrics.CLI_MAIN_FUNCTION_EXECUTION_MODE,
            StringMetrics.CLI_SOURCE_MAP_EMBED_SOURCES_MODE,
            StringMetrics.CLI_SOURCE_MAP_NAMES_POLICY,
            StringMetrics.CLI_WASM_TARGET_MODE,
            StringMetrics.CLI_JS_IR_SAFE_EXTERNAL_BOOLEAN_DIAGNOSTIC_MODE,
            StringMetrics.CLI_NATIVE_LIGHT_DEBUG_MODE,
            StringMetrics.CLI_NATIVE_ALLOCATOR,
            StringMetrics.CLI_NATIVE_DEBUG_INFO_FORMAT_VERSION,
            StringMetrics.CLI_NATIVE_DEBUG_TRAMPOLINE_MODE,
            StringMetrics.CLI_NATIVE_IR_PROPERTY_LAZY_INITIALIZATION_MODE,
            StringMetrics.CLI_NATIVE_PRE_LINK_CACHES_MODE,
            StringMetrics.CLI_NATIVE_MEMORY_MODEL,
            StringMetrics.CLI_NATIVE_PRODUCE_OUTPUT_KIND,
            StringListMetrics.CLI_MANUALLY_CONFIGURED_LANGUAGE_FEATURES,
            StringListMetrics.CLI_JSR305_NULLABILITY_ANNOTATIONS_MODE,
            StringListMetrics.CLI_METADATA_TARGET_PLATFORM,
            StringListMetrics.CLI_NATIVE_MANIFEST_TARGETS,
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
    BuildReports,
    JsTestBrowser,
    SwiftExport,
    CompilerArguments,
}
