// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName", "LocalVariableName", "UNUSED_VARIABLE")

package org.jetbrains.kotlin.gradle.multiplatformTests.testProperties

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import kotlin.reflect.KProperty

/**
 * Declare simple properties in this method like this:
 *
 * val my_property by simplePropertyWithValue("my-default-value")
 *
 * This will tell tests' infrastructure to substitute all occurrences of {{my_property}}
 * in the test data with "my-default-value".
 */
internal fun SimpleProperties(
    gradleVersion: GradleVersion,
    kotlinVersion: KotlinToolingVersion,
    agpVersion: String? = null
) : Map<String, String> {
    val isAgp9OrHigher = (agpVersion?.substringBefore('.')?.toIntOrNull() ?: 0) >= 9
    val result: MutableMap<String, String> = mutableMapOf()

    fun simplePropertyWithValue(defaultValue: String) = object {
            operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): MutableMap<String, String> {
                result[property.name] = defaultValue
                return result
            }
        }

    val kts_kotlin_plugin_repositories by simplePropertyWithValue(
        GradleKotlinTestUtils.listRepositories(useKts = true, gradleVersion, kotlinVersion)
    )
    val kotlin_plugin_repositories by simplePropertyWithValue(
        GradleKotlinTestUtils.listRepositories(useKts = false, gradleVersion, kotlinVersion)
    )

    val compile_sdk_version by simplePropertyWithValue("31")
    val buildToolsVersion by simplePropertyWithValue("28.0.3")

    val androidSdkMethod = if (isAgp9OrHigher) "compileSdk = $compile_sdk_version" else "compileSdkVersion($compile_sdk_version)"

    val compileOptions = if (isAgp9OrHigher) "" else """
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    """.trimIndent()

    val defaultAndroidBlockBody = """
        $androidSdkMethod
        ${if (isAgp9OrHigher) "" else "buildToolsVersion(\"$buildToolsVersion\")"}
        namespace = "org.jetbrains.kotlin.mpp.tests"
        $compileOptions
    """.trimIndent()

    val androidTargetCall = if (kotlinVersion < KotlinToolingVersion("2.1.0")) "android()" else "androidTarget()"

    val android_library_configuration_open by simplePropertyWithValue(
        if (isAgp9OrHigher) "kotlin {\n    androidLibrary {" else "android {"
    )

    val android_library_configuration_close by simplePropertyWithValue(
        if (isAgp9OrHigher) "        withHostTest {}\n    }\n}" else "}"
    )

    val android_target by simplePropertyWithValue(
        if (isAgp9OrHigher) "" else androidTargetCall
    )

    val android_compile_sdk by simplePropertyWithValue(androidSdkMethod)

    val android_legacy_build_types by simplePropertyWithValue(
        if (isAgp9OrHigher) "" else """
            val debug by buildTypes.getting
            debug.matchingFallbacks += listOf("debug", "release")
        """.trimIndent()
    )

    val android_legacy_main_manifest by simplePropertyWithValue(
        if (isAgp9OrHigher) "" else "sourceSets.getByName(\"main\").manifest.srcFile(\"src/androidMain/AndroidManifest.xml\")"
    )

    val default_android_block by simplePropertyWithValue(
        if (isAgp9OrHigher) """
            kotlin {
                androidLibrary {
                    $defaultAndroidBlockBody
                    withHostTest {}
                }
            }
        """.trimIndent()
        else """
            android {
                $defaultAndroidBlockBody
            }
        """.trimIndent()
    )

    val android_main_kotlin_source_dirs by simplePropertyWithValue(
        if (isAgp9OrHigher) "kotlin.srcDirs(\"src/main/kotlin\")" else ""
    )

    val android_host_test_source_dirs by simplePropertyWithValue(
        if (isAgp9OrHigher) """
            val androidHostTest by getting {
                kotlin.srcDirs("src/androidUnitTest/kotlin")
            }
        """.trimIndent() else ""
    )

    val android_library_plugin_id by simplePropertyWithValue(
        if (isAgp9OrHigher) "id(\"com.android.kotlin.multiplatform.library\")"
        else "id(\"com.android.library\")"
    )

    val android_application_compatible_plugin_id by simplePropertyWithValue(
        "id(\"com.android.application\")"
    )

    val android_root_plugins_apply_false by simplePropertyWithValue(
        if (isAgp9OrHigher) {
            """
            id("com.android.kotlin.multiplatform.library") apply false
            id("com.android.application") apply false
            """.trimIndent()
        }
        else {
            """
            id("com.android.library") apply false
            id("com.android.application") apply false
            """.trimIndent()
        }
    )

    val android_root_plugins_with_versions by simplePropertyWithValue(
        if (isAgp9OrHigher) {
            """
            id("com.android.kotlin.multiplatform.library") version "{{agp_version}}"
            id("com.android.application") version "{{agp_version}}"
            """.trimIndent()
        }
        else {
            """
            id("com.android.library") version "{{agp_version}}"
            id("com.android.application") version "{{agp_version}}"
            """.trimIndent()
        }
    )

    val android_library_kotlin_plugin by simplePropertyWithValue(
        if (isAgp9OrHigher) "kotlin(\"multiplatform\")" else "kotlin(\"android\")"
    )

    val android_library_kotlin_plugin_declaration by simplePropertyWithValue(
        if (isAgp9OrHigher) "kotlin(\"multiplatform\")" else "kotlin(\"multiplatform\") apply false"
    )

    val android_library_manual_publication_snippet by simplePropertyWithValue(
        if (isAgp9OrHigher) "" else """
            afterEvaluate {
                publications {
                    create<MavenPublication>("default") {
                        from(components["release"])
                    }
                }
            }
        """.trimIndent()
    )

    val android_main_source_set_open by simplePropertyWithValue(
        if (isAgp9OrHigher) """
            kotlin {
                sourceSets {
                    val androidMain by getting {
        """.trimIndent() else ""
    )

    val android_main_source_set_close by simplePropertyWithValue(
        if (isAgp9OrHigher) """
                    }
                }
            }
        """.trimIndent() else ""
    )

    val android_target_publishing_snippet by simplePropertyWithValue(
        if (isAgp9OrHigher) ""
        else """
            $androidTargetCall {
                publishLibraryVariants("release", "debug")
            }
        """.trimIndent()
    )

    val target_hierarchy_toggle =
        if (kotlinVersion <= KotlinToolingVersion("1.9.0")) "targetHierarchy.default()"
        else ""
    val target_hierarchy by simplePropertyWithValue(target_hierarchy_toggle)

    return result
}
