// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object KotlinArtifactNames {
    const val JETBRAINS_ANNOTATIONS: String = "annotations-13.0.jar"
    const val KOTLIN_STDLIB: String = "kotlin-stdlib.jar"
    const val KOTLIN_STDLIB_SOURCES: String = "kotlin-stdlib-sources.jar"
    const val KOTLIN_STDLIB_JDK7: String = "kotlin-stdlib-jdk7.jar"
    const val KOTLIN_STDLIB_JDK7_SOURCES: String = "kotlin-stdlib-jdk7-sources.jar"
    const val KOTLIN_STDLIB_JDK8: String = "kotlin-stdlib-jdk8.jar"
    const val KOTLIN_STDLIB_JDK8_SOURCES: String = "kotlin-stdlib-jdk8-sources.jar"
    const val KOTLIN_REFLECT: String = "kotlin-reflect.jar"
    const val KOTLIN_STDLIB_JS: String = "kotlin-stdlib-js.klib"
    const val KOTLIN_TEST: String = "kotlin-test.jar"
    const val KOTLIN_TEST_JUNIT: String = "kotlin-test-junit.jar"
    const val KOTLIN_TEST_JS: String = "kotlin-test-js.klib"
    const val KOTLIN_MAIN_KTS: String = "kotlin-main-kts.jar"
    const val KOTLIN_SCRIPT_RUNTIME: String = "kotlin-script-runtime.jar"
    const val KOTLIN_SCRIPTING_COMMON: String = "kotlin-scripting-common.jar"
    const val KOTLIN_SCRIPTING_JVM: String = "kotlin-scripting-jvm.jar"
    const val KOTLIN_COMPILER: String = "kotlin-compiler.jar"
    const val KOTLIN_PRELOADER: String = "kotlin-preloader.jar"
    const val LOMBOK_COMPILER_PLUGIN: String = "lombok-compiler-plugin.jar"
    const val KOTLIN_ANNOTATIONS_JVM: String = "kotlin-annotations-jvm.jar"
    const val TROVE4J: String = "trove4j.jar"
    const val KOTLIN_DAEMON: String = "kotlin-daemon.jar"
    const val KOTLIN_SCRIPTING_COMPILER: String = "kotlin-scripting-compiler.jar"
    const val KOTLIN_SCRIPTING_COMPILER_IMPL: String = "kotlin-scripting-compiler-impl.jar"
    const val ALLOPEN_COMPILER_PLUGIN: String = "allopen-compiler-plugin.jar"
    const val NOARG_COMPILER_PLUGIN: String = "noarg-compiler-plugin.jar"
    const val SAM_WITH_RECEIVER_COMPILER_PLUGIN: String = "sam-with-receiver-compiler-plugin.jar"
    const val ASSIGNMENT_COMPILER_PLUGIN: String = "assignment-compiler-plugin.jar"
    const val KOTLINX_SERIALIZATION_COMPILER_PLUGIN: String = "kotlinx-serialization-compiler-plugin.jar"
    const val PARCELIZE_COMPILER_PLUGIN: String = "parcelize-compiler.jar"
    const val PARCELIZE_RUNTIME: String = "parcelize-runtime.jar"
    const val ANDROID_EXTENSIONS_RUNTIME: String = "android-extensions-runtime.jar"
    const val POWER_ASSERT_COMPILER_PLUGIN: String = "power-assert-compiler-plugin.jar"
    const val KOTLIN_DATAFRAME_COMPILER_PLUGIN: String = "kotlin-dataframe-compiler-plugin.jar"

    const val KOTLINC: String = "kotlinc"
}