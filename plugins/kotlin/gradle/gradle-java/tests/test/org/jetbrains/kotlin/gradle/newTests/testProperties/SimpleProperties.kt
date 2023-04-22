// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package org.jetbrains.kotlin.gradle.newTests.testProperties

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import kotlin.reflect.KProperty

@Suppress("UNUSED_VARIABLE")
fun SimpleProperties(gradleVersion: GradleVersion, kgpVersion: KotlinToolingVersion) : Map<String, String> {
    val result: MutableMap<String, String> = mutableMapOf()

    fun simplePropertyWithValue(defaultValue: String) = object {
            operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): MutableMap<String, String> {
                result[property.name] = defaultValue
                return result
            }
        }

    val kts_kotlin_plugin_repositories by simplePropertyWithValue(
        GradleKotlinTestUtils.listRepositories(useKts = true, gradleVersion, kgpVersion)
    )
    val kotlin_plugin_repositories by simplePropertyWithValue(
        GradleKotlinTestUtils.listRepositories(useKts = false, gradleVersion, kgpVersion)
    )

    val compile_sdk_version by simplePropertyWithValue("31")
    val buildToolsVersion by simplePropertyWithValue("28.0.3")

    val default_android_block_body by simplePropertyWithValue("""
        compileSdkVersion($compile_sdk_version)
        buildToolsVersion("$buildToolsVersion")
        namespace = "org.jetbrains.kotlin.mpp.tests"
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    """.trimIndent())

    val default_android_block by simplePropertyWithValue("""
        android {
            $default_android_block_body
        }
    """.trimIndent())

    return result
}
