// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.isKgpDependencyResolutionEnabled
import org.jetbrains.kotlin.idea.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import java.io.PrintStream

abstract class HmppImportAndHighlightingTests : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName(): String = "hmppImportAndHighlighting"
    override fun clearTextFromMarkup(text: String): String = clearTextFromDiagnosticMarkup(text)

    class TestBucket1322 : HmppImportAndHighlightingTests() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.6.0+")
        fun testMultiModulesHmpp() {
            val macosX64 = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
            val linuxX64 = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64)
            val iosX64 = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.IOS_X64)
            val iosArm64 = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.IOS_ARM64)
            val jvm = JvmPlatforms.defaultJvmPlatform
            val js = JsPlatforms.defaultJsPlatform
            val allNative = NativePlatforms.unspecifiedNativePlatform

            configureAndImportProject()

            checkProjectStructure(true, false, false) {
                allModules { assertExhaustiveModuleDependencyList() }
                module("multimod-hmpp") { targetPlatform(jvm) }
                module("multimod-hmpp.api-jvm") { targetPlatform(jvm) }
                module("multimod-hmpp.api-jvm.main") { targetPlatform(jvm) }
                module("multimod-hmpp.api-jvm.test") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                    if (kotlinPluginVersion >= KotlinToolingVersion("1.8.20-dev-1629")) // KTIJ-23756
                        moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.RUNTIME)
                }
                module("multimod-hmpp.bottom-mpp") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.bottom-mpp.commonMain") {
                    targetPlatform(jvm, allNative)
                }
                module("multimod-hmpp.bottom-mpp.commonTest") {
                    targetPlatform(jvm, allNative)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.bottom-mpp.iosSimLibMain") {
                    targetPlatform(iosX64)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                    if (HostManager.hostIsMac)
                        moduleDependency("multimod-hmpp.top-mpp.dummyiOSMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.bottom-mpp.iosSimLibTest") {
                    targetPlatform(iosX64)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.iosSimLibMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST)
                    if (HostManager.hostIsMac)
                        moduleDependency("multimod-hmpp.top-mpp.dummyiOSMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.bottom-mpp.jvm11Main") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.bottom-mpp.jvm11Test") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvm11Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvm11Main", DependencyScope.RUNTIME)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Test", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.TEST)
                }

                module("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.bottom-mpp.jvmJavaJvm11Test") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST, isOptional = true)
                }
                module("multimod-hmpp.bottom-mpp.jvmWithJavaMain") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jvm18Main", DependencyScope.COMPILE)
                    // `allowMultiple` flags for the next three moduleDependency should be removed
                    // after https://youtrack.jetbrains.com/issue/KTIJ-15745 fixed
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE, allowMultiple = true)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE, allowMultiple = true)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE, allowMultiple = true)
                }
                module("multimod-hmpp.bottom-mpp.jvmWithJavaTest") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Test", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaMain", DependencyScope.RUNTIME, isOptional = true)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jvm18Main", DependencyScope.TEST)
                    // `allowMultiple` flags for the next three moduleDependency should be removed
                    // after https://youtrack.jetbrains.com/issue/KTIJ-15745 fixed
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST, allowMultiple = true)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST, allowMultiple = true)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST, allowMultiple = true)
                }
                module("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain") {
                    targetPlatform(jvm, allNative)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.bottom-mpp.jvmWithJavaiOSTest") {
                    targetPlatform(jvm, allNative)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST)
                }
                module("multimod-hmpp.bottom-mpp.main") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.bottom-mpp.test") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.mpp-additional") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.mpp-additional.commonMain") {
                    targetPlatform(jvm, js, allNative)
                }
                module("multimod-hmpp.mpp-additional.commonTest") {
                    targetPlatform(jvm, js, allNative)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.mpp-additional.iosArm64Main") {
                    targetPlatform(iosArm64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.iosArm64Test") {
                    targetPlatform(iosArm64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosArm64Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosTest", DependencyScope.TEST)
                }
                module("multimod-hmpp.mpp-additional.iosMacosMain") {
                    targetPlatform(macosX64, iosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.iosMain") {
                    targetPlatform(iosArm64, iosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.iosTest") {
                    targetPlatform(iosArm64, iosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.mpp-additional.iosX64Main") {
                    targetPlatform(iosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMacosMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.iosX64Test") {
                    targetPlatform(iosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMacosMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosX64Main", DependencyScope.TEST)
                }
                module("multimod-hmpp.mpp-additional.jsLinuxMain") {
                    targetPlatform(js, allNative)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.jsMain") {
                    targetPlatform(js)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.jsLinuxMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsMain", DependencyScope.COMPILE)
                    moduleDependency(
                        "multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                    moduleDependency(
                        "multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                    moduleDependency(
                        "multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.COMPILE,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                    moduleDependency(
                        "multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                }
                module("multimod-hmpp.mpp-additional.jsTest") {
                    targetPlatform(js)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jsLinuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jsMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jsMain", DependencyScope.RUNTIME)
                    moduleDependency("multimod-hmpp.top-mpp.jsMain", DependencyScope.TEST)
                    moduleDependency(
                        "multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                    moduleDependency(
                        "multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                    moduleDependency(
                        "multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.TEST,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                    moduleDependency(
                        "multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST,
                        allowMultiple = kotlinPluginVersion < KotlinToolingVersion("1.5.30")
                    )
                }
                module("multimod-hmpp.mpp-additional.jvmMacosMain") {
                    targetPlatform(jvm, allNative)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.jvmMain") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.jvmMacosMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.jvmTest") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jvmMacosMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jvmMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jvmMain", DependencyScope.RUNTIME)
                }
                module("multimod-hmpp.mpp-additional.linuxMain") {
                    targetPlatform(linuxX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.jsLinuxMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.macosLinuxMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.linuxMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.linuxTest") {
                    targetPlatform(linuxX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jsLinuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.linuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.macosLinuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.linuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.mpp-additional.macosLinuxMain") {
                    targetPlatform(macosX64, linuxX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.macosMain") {
                    targetPlatform(macosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMacosMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.jvmMacosMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.mpp-additional.macosLinuxMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.mpp-additional.macosTest") {
                    targetPlatform(macosX64)
                    moduleDependency("multimod-hmpp.mpp-additional.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.iosMacosMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.jvmMacosMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.macosLinuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.mpp-additional.macosMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.plain-jvm") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.plain-jvm.main") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jvm18Main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.plain-jvm.test") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.plain-jvm.main", DependencyScope.COMPILE)
                    if (kotlinPluginVersion >= KotlinToolingVersion("1.8.20-dev-1629")) // KTIJ-23756
                        moduleDependency("multimod-hmpp.plain-jvm.main", DependencyScope.RUNTIME)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmJavaJvm11Main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.bottom-mpp.jvmWithJavaiOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jvm18Main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.top-mpp.commonMain") {
                    targetPlatform(allNative, js, jvm)
                }
                module("multimod-hmpp.top-mpp.commonTest") {
                    targetPlatform(allNative, js, jvm)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.dummyiOSMain") {
                    targetPlatform(iosX64)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.dummyiOSTest") {
                    targetPlatform(iosX64)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.dummyiOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.jsJvm18iOSMain") {
                    targetPlatform(allNative, js, jvm)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.jsJvm18iOSTest") {
                    targetPlatform(allNative, js, jvm)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.jsLinuxMain") {
                    targetPlatform(allNative, js)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.jsLinuxTest") {
                    targetPlatform(allNative, js)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.jsMain") {
                    targetPlatform(js)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.jsTest") {
                    targetPlatform(js)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsMain", DependencyScope.RUNTIME)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.jvm18Main") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.jvm18Test") {
                    targetPlatform(jvm)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsJvm18iOSTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jvm18Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jvm18Main", DependencyScope.RUNTIME)
                    moduleDependency("multimod-hmpp.top-mpp.kt27816Main", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.api-jvm.main", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.kt27816Main") {
                    targetPlatform(allNative, js, jvm)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.linuxMain") {
                    targetPlatform(linuxX64)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.linuxTest") {
                    targetPlatform(linuxX64)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.jsLinuxTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.linuxMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.macosMain") {
                    targetPlatform(macosX64)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.COMPILE)
                }
                module("multimod-hmpp.top-mpp.macosTest") {
                    targetPlatform(macosX64)
                    moduleDependency("multimod-hmpp.top-mpp.commonMain", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.commonTest", DependencyScope.TEST)
                    moduleDependency("multimod-hmpp.top-mpp.macosMain", DependencyScope.TEST)
                }
                module("multimod-hmpp.top-mpp.main") {
                    targetPlatform(jvm)
                }
                module("multimod-hmpp.top-mpp.test") {
                    targetPlatform(jvm)
                }
            }

            checkHighlightingOnAllModules()
        }

        @Test
        @PluginTargetVersions(pluginVersion = "1.6.0+")
        fun testKt46625SupportedAndUnsupportedPlatform() {
            configureByFiles()
            importProject()
            checkHighlightingOnAllModules()
        }

        @Test
        @PluginTargetVersions(pluginVersion = "1.8.0-dev-0+")
        fun testKtij22345SyntheticJavaProperties() {
            configureByFiles()
            importProject()
            createHighlightingCheck(testLineMarkers = false).invokeOnAllModules()
        }

        @Test
        @PluginTargetVersions(pluginVersion = "1.7.0+")
        fun testKotlinJavaKotlin() {
            configureByFiles()
            importProject()
            createHighlightingCheck(testLineMarkers = false).invokeOnAllModules()
        }
    }

    class HmppLibAndConsumer25 : HmppImportAndHighlightingTests() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.6.0+")
        fun testHmppLibAndConsumer() {
            configureByFiles()
            linkProject("$projectPath/lib-and-app")
            linkProject("$projectPath/published-lib-consumer")

            checkHighlightingOnAllModules()

            val publishTaskSettings = ExternalSystemTaskExecutionSettings()
            publishTaskSettings.externalProjectPath = "$projectPath/lib-and-app"
            publishTaskSettings.taskNames = listOf("publish")
            publishTaskSettings.externalSystemIdString = GradleConstants.SYSTEM_ID.id

            ExternalSystemUtil.runTask(
                publishTaskSettings,
                DefaultRunExecutor.EXECUTOR_ID,
                myProject,
                GradleConstants.SYSTEM_ID,
                null,
                ProgressExecutionMode.NO_PROGRESS_SYNC
            )

            runInEdtAndWait { VirtualFileManager.getInstance().syncRefresh() }
            ExternalSystemUtil.refreshProject("$projectPath/published-lib-consumer", createImportSpec())

            createHighlightingCheck(correspondingFilePostfix = ".after").invokeOnAllModules()

            val muteP2POnUnsupportedHost = !HostManager.hostIsMac && isKgpDependencyResolutionEnabled() // KTIJ-24578

            checkProjectStructure(
                exhaustiveModuleList = true,
                exhaustiveSourceSourceRootList = false,
                exhaustiveDependencyList = false
            ) {
                allModules {
                    assertNoDependencyInBuildClasses()
                }

                module("lib-and-app")

                module("lib-and-app.app")

                module("lib-and-app.app.commonMain") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.app.commonTest") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                }

                module("lib-and-app.app.iosArm64Main") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE, isOptional = muteP2POnUnsupportedHost)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE, isOptional = muteP2POnUnsupportedHost)
                    if (HostManager.hostIsMac)
                        moduleDependency("lib-and-app.lib.iosArm64Main", DependencyScope.COMPILE)
                }

                module("lib-and-app.app.iosArm64Test") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosArm64Main", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST, isOptional = muteP2POnUnsupportedHost)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST, isOptional = muteP2POnUnsupportedHost)
                }

                module("lib-and-app.app.iosMain") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.app.iosTest") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST)
                }

                module("lib-and-app.app.iosX64Main") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE, isOptional = muteP2POnUnsupportedHost)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE, isOptional = muteP2POnUnsupportedHost)
                    if (HostManager.hostIsMac)
                        moduleDependency("lib-and-app.lib.iosX64Main", DependencyScope.COMPILE)
                }

                module("lib-and-app.app.iosSimulatorArm64Main", isOptional = true) {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE)
                    if (HostManager.hostIsMac)
                        moduleDependency("lib-and-app.lib.iosSimulatorArm64Main", DependencyScope.COMPILE)
                }

                module("lib-and-app.app.iosX64Test") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosX64Main", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST, isOptional = muteP2POnUnsupportedHost)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST, isOptional = muteP2POnUnsupportedHost)
                    if (HostManager.hostIsMac)
                        moduleDependency("lib-and-app.lib.iosX64Main", DependencyScope.TEST)
                }

                module("lib-and-app.app.iosSimulatorArm64Test", isOptional = true) {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosSimulatorArm64Main", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.iosTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST)
                    if (HostManager.hostIsMac)
                        moduleDependency("lib-and-app.lib.iosSimulatorArm64Main", DependencyScope.TEST)
                }

                module("lib-and-app.app.jsMain") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.app.jvmAndJsMain", DependencyScope.COMPILE, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.jsMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.COMPILE, allowMultiple = true)
                }

                module("lib-and-app.app.jsTest") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.jsMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.jsMain", DependencyScope.RUNTIME, isOptional = true)
                    moduleDependency("lib-and-app.app.jvmAndJsMain", DependencyScope.TEST, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.jsMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.TEST, allowMultiple = true)
                }

                module("lib-and-app.app.jvmAndJsMain") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.app.jvmMain") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.app.jvmAndJsMain", DependencyScope.COMPILE, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.jvmMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.COMPILE, allowMultiple = true)
                }

                module("lib-and-app.app.jvmTest") {
                    moduleDependency("lib-and-app.app.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.jvmAndJsMain", DependencyScope.TEST, allowMultiple = true)
                    moduleDependency("lib-and-app.app.jvmMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.app.jvmMain", DependencyScope.RUNTIME, isOptional = true)
                    moduleDependency("lib-and-app.lib.jvmMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST, allowMultiple = true)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.TEST, allowMultiple = true)
                }

                module("lib-and-app.lib")

                module("lib-and-app.lib.commonMain")

                module("lib-and-app.lib.commonTest") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                }

                module("lib-and-app.lib.iosArm64Main") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.iosArm64Test") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosArm64Main", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosTest", DependencyScope.TEST)
                }

                module("lib-and-app.lib.iosMain") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.iosTest") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST)
                }

                module("lib-and-app.lib.iosX64Main") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.iosSimulatorArm64Main", isOptional = true) {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.iosX64Test") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosX64Main", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosTest", DependencyScope.TEST)
                }

                module("lib-and-app.lib.iosSimulatorArm64Test", isOptional = true) {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosSimulatorArm64Main", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.iosTest", DependencyScope.TEST)
                }

                module("lib-and-app.lib.jsMain") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.jsTest") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jsMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jsMain", DependencyScope.RUNTIME, isOptional = true)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jvmAndJsTest", DependencyScope.TEST)
                }

                module("lib-and-app.lib.jvmAndJsMain") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.jvmAndJsTest") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.TEST)
                }

                module("lib-and-app.lib.jvmMain") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.COMPILE)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.COMPILE)
                }

                module("lib-and-app.lib.jvmTest") {
                    moduleDependency("lib-and-app.lib.commonMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.commonTest", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jvmMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jvmMain", DependencyScope.RUNTIME, isOptional = true)
                    moduleDependency("lib-and-app.lib.jvmAndJsMain", DependencyScope.TEST)
                    moduleDependency("lib-and-app.lib.jvmAndJsTest", DependencyScope.TEST)
                }

                module("published-lib-consumer")

                module("published-lib-consumer.commonMain") {
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:commonMain:1.0", DependencyScope.COMPILE)
                }

                module("published-lib-consumer.commonTest") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:commonMain:1.0", DependencyScope.TEST)
                }

                module("published-lib-consumer.iosArm64Main") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.COMPILE)
                    libraryDependency(Regex("Gradle: com.h0tk3y.mpp.demo:lib-iosarm64:(klib:)?1.0"), DependencyScope.COMPILE)
                }

                module("published-lib-consumer.iosArm64Test") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.commonTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosArm64Main", DependencyScope.TEST)
                    libraryDependency(Regex("Gradle: com.h0tk3y.mpp.demo:lib-iosarm64:(klib:)?1.0"), DependencyScope.TEST)
                }

                module("published-lib-consumer.iosMain") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:commonMain:1.0", DependencyScope.COMPILE)
                    libraryDependency(
                        "Gradle: com.h0tk3y.mpp.demo:lib:iosMain:1.0", DependencyScope.COMPILE,
                        isOptional = !HostManager.hostIsMac
                    )
                }

                module("published-lib-consumer.iosTest") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.commonTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.TEST)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:commonMain:1.0", DependencyScope.TEST)
                    libraryDependency(
                        "Gradle: com.h0tk3y.mpp.demo:lib:iosMain:1.0", DependencyScope.TEST,
                        isOptional = !HostManager.hostIsMac
                    )
                }

                module("published-lib-consumer.iosX64Main") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.COMPILE)
                    libraryDependency(Regex("Gradle: com.h0tk3y.mpp.demo:lib-iosx64:(klib:)?1.0"), DependencyScope.COMPILE)
                }

                module("published-lib-consumer.iosSimulatorArm64Main", isOptional = true) {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.COMPILE)
                    libraryDependency(Regex("Gradle: com.h0tk3y.mpp.demo:lib-iossimulatorarm64:(klib:)?1.0"), DependencyScope.COMPILE)
                }

                module("published-lib-consumer.iosX64Test") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.commonTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosX64Main", DependencyScope.TEST)
                    libraryDependency(Regex("Gradle: com.h0tk3y.mpp.demo:lib-iosx64:(klib:)?1.0"), DependencyScope.TEST)
                }

                module("published-lib-consumer.iosSimulatorArm64Test", isOptional = true) {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.commonTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.iosSimulatorArm64Main", DependencyScope.TEST)
                    libraryDependency(Regex("Gradle: com.h0tk3y.mpp.demo:lib-iossimulatorarm64:(klib:)?1.0"), DependencyScope.TEST)
                }

                module("published-lib-consumer.jsMain") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    moduleDependency("published-lib-consumer.jvmAndJsMain", DependencyScope.COMPILE)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:all:1.0", DependencyScope.COMPILE, isOptional = true)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-js:1.0", DependencyScope.COMPILE)
                }

                module("published-lib-consumer.jsTest") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.commonTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.jvmAndJsMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.jsMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.jsMain", DependencyScope.RUNTIME, isOptional = true)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:all:1.0", DependencyScope.TEST, isOptional = true)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-js:1.0", DependencyScope.TEST)
                }

                module("published-lib-consumer.jvmMain") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    moduleDependency("published-lib-consumer.jvmAndJsMain", DependencyScope.COMPILE)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:all:1.0", DependencyScope.COMPILE, isOptional = true)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-jvm:1.0", DependencyScope.COMPILE)
                }

                module("published-lib-consumer.jvmTest") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.commonTest", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.jvmAndJsMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.jvmMain", DependencyScope.TEST)
                    moduleDependency("published-lib-consumer.jvmMain", DependencyScope.RUNTIME, isOptional = true)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:all:1.0", DependencyScope.TEST, isOptional = true)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-jvm:1.0", DependencyScope.TEST)
                }

                module("published-lib-consumer.jvmAndJsMain") {
                    moduleDependency("published-lib-consumer.commonMain", DependencyScope.COMPILE)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:commonMain:1.0", DependencyScope.COMPILE)
                    libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:jvmAndJsMain:1.0", DependencyScope.COMPILE)
                }
            }
        }
    }

    protected fun configureAndImportProject() {
        configureByFiles()
        importProject()
    }

    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)
}
