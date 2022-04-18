// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.checkers.utils.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.KotlinVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.compareTo
import org.jetbrains.kotlin.idea.codeInsight.gradle.kotlinPluginVersionMatches
import org.jetbrains.kotlin.idea.codeInsight.gradle.parseKotlinVersion
import org.jetbrains.kotlin.idea.gradleTooling.OrphanSourceSetsImportingDiagnostic
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.test.domain.ProjectEntity
import org.jetbrains.kotlin.test.matcher.checkProjectEntity
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.After
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.PrintStream

class HierarchicalMultiplatformProjectImportingTest : MultiplePluginVersionGradleImportingTestCase() {

    private var isSdkCreationCheckerSuppressed = false

    @After
    fun after() {
        GradleDaemonServices.stopDaemons() // Workaround until KTIJ-19669 fixed
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.3.30+")
    fun testImportHMPPFlag() {
        configureByFiles()
        importProject()

        checkProjectStructure(
            exhaustiveModuleList = false,
            exhaustiveSourceSourceRootList = false,
            exhaustiveDependencyList = false
        ) {
            allModules {
                isHMPP(true)
                assertNoDependencyInBuildClasses()
            }
            module("my-app.commonMain")
            module("my-app.jvmAndJsMain")
        }

        val messageCollector = MessageCollector()

        checkProjectEntity(
            ProjectEntity.importFromOpenapiProject(myProject, projectPath),
            messageCollector,
            exhaustiveModuleList = false,
            exhaustiveSourceSourceRootList = false,
            exhaustiveDependencyList = false,
            exhaustiveTestsList = false,
        ) {
            allModules {
                isHMPP(true)
                assertNoDependencyInBuildClasses()
            }
            module("my-app.commonMainPop")
            module("my-app.jvmAndJsMain")
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.3.30+")
    fun testImportIntermediateModules() {
        configureByFiles()
        importProject()

        checkProjectStructure {
            allModules { assertNoDependencyInBuildClasses() }
            module("my-app")

            module("my-app.commonMain") {
                isHMPP(true)
                targetPlatform(
                    JsPlatforms.defaultJsPlatform,
                    JvmPlatforms.jvm6,
                    NativePlatforms.unspecifiedNativePlatform
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}",
                    DependencyScope.COMPILE
                )
                sourceFolder("src/commonMain/kotlin", SourceKotlinRootType)
                sourceFolder("src/commonMain/resources", ResourceKotlinRootType)
            }

            module("my-app.commonTest") {
                isHMPP(true)
                targetPlatform(
                    JsPlatforms.defaultJsPlatform,
                    JvmPlatforms.jvm6,
                    NativePlatforms.unspecifiedNativePlatform
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                sourceFolder("src/commonTest/kotlin", TestSourceKotlinRootType)
                sourceFolder("src/commonTest/resources", TestResourceKotlinRootType)
            }

            module("my-app.jsMain") {
                isHMPP(true)
                targetPlatform(JsPlatforms.defaultJsPlatform)
                if (!kotlinPluginVersionMatches("1.6.0-dev+")) {
                    libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}", DependencyScope.COMPILE)
                }
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}",
                    DependencyScope.COMPILE
                )
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                moduleDependency("my-app.jvmAndJsMain", DependencyScope.COMPILE)
                moduleDependency("my-app.linuxAndJsMain", DependencyScope.COMPILE)
                sourceFolder("src/jsMain/kotlin", SourceKotlinRootType)
                sourceFolder("src/jsMain/resources", ResourceKotlinRootType)
            }

            module("my-app.jsTest") {
                isHMPP(true)
                targetPlatform(JsPlatforms.defaultJsPlatform)
                if (!kotlinPluginVersionMatches("1.6.0-dev+")) {
                    libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}", DependencyScope.TEST)
                }
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )

                if (!kotlinPluginVersionMatches("1.6.20-dev+")) {
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:${kotlinPluginVersionString}",
                        DependencyScope.TEST
                    )
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlin:kotlin-test-common:${kotlinPluginVersionString}",
                        DependencyScope.TEST
                    )
                }

                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-js:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.TEST)
                moduleDependency(
                    "my-app.jsMain",
                    DependencyScope.RUNTIME
                )  // Temporary dependency, need to remove after KT-40551 is solved
                moduleDependency("my-app.jvmAndJsMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmAndJsTest", DependencyScope.TEST)
                moduleDependency("my-app.linuxAndJsMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxAndJsTest", DependencyScope.TEST)
                sourceFolder("src/jsTest/kotlin", TestSourceKotlinRootType)
                sourceFolder("src/jsTest/resources", TestResourceKotlinRootType)
            }

            module("my-app.jvmAndJsMain") {
                isHMPP(true)
                targetPlatform(JsPlatforms.defaultJsPlatform, JvmPlatforms.jvm6)
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}",
                    DependencyScope.COMPILE
                )
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                sourceFolder("src/jvmAndJsMain/kotlin", SourceKotlinRootType)
                sourceFolder("src/jvmAndJsMain/resources", ResourceKotlinRootType)
            }

            module("my-app.jvmAndJsTest") {
                isHMPP(true)
                targetPlatform(JsPlatforms.defaultJsPlatform, JvmPlatforms.jvm6)
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmAndJsMain", DependencyScope.TEST)
                sourceFolder("src/jvmAndJsTest/kotlin", TestSourceKotlinRootType)
                sourceFolder("src/jvmAndJsTest/resources", TestResourceKotlinRootType)
            }

            module("my-app.jvmMain") {
                isHMPP(true)
                targetPlatform(JvmPlatforms.jvm6)

                if (!kotlinPluginVersionMatches("1.5.30+")) {
                    libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}", DependencyScope.COMPILE)
                }
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}",
                    DependencyScope.COMPILE
                )
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.COMPILE)
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                moduleDependency("my-app.jvmAndJsMain", DependencyScope.COMPILE)
                sourceFolder("src/jvmMain/kotlin", JavaSourceRootType.SOURCE)
                sourceFolder("src/jvmMain/resources", JavaResourceRootType.RESOURCE)
            }

            module("my-app.jvmTest") {
                isHMPP(true)
                targetPlatform(JvmPlatforms.jvm6)
                libraryDependency(Regex("Gradle: junit:junit:[0-9.]+"), DependencyScope.TEST)
                libraryDependency("Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST)

                if (!kotlinPluginVersionMatches("1.5.30+")) {
                    libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}", DependencyScope.TEST)
                }

                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )

                if (!kotlinPluginVersionMatches("1.6.20-dev+")) {
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:${kotlinPluginVersionString}",
                        DependencyScope.TEST
                    )
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlin:kotlin-test-common:${kotlinPluginVersionString}",
                        DependencyScope.TEST
                    )
                }
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-junit:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmAndJsMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmAndJsTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.TEST)
                moduleDependency(
                    "my-app.jvmMain",
                    DependencyScope.RUNTIME
                )  // Temporary dependency, need to remove after KT-40551 is solved
                sourceFolder("src/jvmTest/kotlin", JavaSourceRootType.TEST_SOURCE)
                sourceFolder("src/jvmTest/resources", JavaResourceRootType.TEST_RESOURCE)
            }

            module("my-app.linuxAndJsMain") {
                isHMPP(true)
                targetPlatform(JsPlatforms.defaultJsPlatform, NativePlatforms.unspecifiedNativePlatform)
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}",
                    DependencyScope.COMPILE
                )
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                sourceFolder("src/linuxAndJsMain/kotlin", SourceKotlinRootType)
                sourceFolder("src/linuxAndJsMain/resources", ResourceKotlinRootType)
            }

            module("my-app.linuxAndJsTest") {
                isHMPP(true)
                targetPlatform(JsPlatforms.defaultJsPlatform, NativePlatforms.unspecifiedNativePlatform)
                sourceFolder("src/linuxAndJsTest/kotlin", TestSourceKotlinRootType)
                sourceFolder("src/linuxAndJsTest/resources", TestResourceKotlinRootType)
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                libraryDependency(
                    "Gradle: org.jetbrains.kotlin:kotlin-test-common:${kotlinPluginVersionString}",
                    DependencyScope.TEST
                )
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.linuxAndJsMain", DependencyScope.TEST)
            }

            module("my-app.linuxX64Main") {
                isHMPP(true)
                targetPlatform(NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64))

                if (!kotlinPluginVersionMatches("1.6.0-dev+")) {
                    libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}", DependencyScope.COMPILE)
                }

                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)

                if (kotlinPluginVersion >= parseKotlinVersion("1.4.0")) {
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64",
                        DependencyScope.PROVIDED
                    )
                } else {
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - builtin",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - iconv",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - linux",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - posix",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - zlib",
                        DependencyScope.PROVIDED
                    )
                }

                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                moduleDependency("my-app.linuxAndJsMain", DependencyScope.COMPILE)
                sourceFolder("src/linuxX64Main/kotlin", SourceKotlinRootType)
                sourceFolder("src/linuxX64Main/resources", ResourceKotlinRootType)
            }

            module("my-app.linuxX64Test") {
                isHMPP(true)
                targetPlatform(NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64))

                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)

                if (!kotlinPluginVersionMatches("1.6.0-dev+")) {
                    libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinPluginVersionString}", DependencyScope.TEST)
                }

                if (!kotlinPluginVersionMatches("1.6.20-dev+")) {
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:${kotlinPluginVersionString}",
                        DependencyScope.TEST
                    )
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlin:kotlin-test-common:${kotlinPluginVersionString}",
                        DependencyScope.TEST
                    )
                }
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - builtin( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - iconv( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - linux( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - posix( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - zlib( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.linuxAndJsMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxAndJsTest", DependencyScope.TEST)
                moduleDependency("my-app.linuxX64Main", DependencyScope.TEST)
                sourceFolder("src/linuxX64Test/kotlin", TestSourceKotlinRootType)
                sourceFolder("src/linuxX64Test/resources", TestResourceKotlinRootType)
            }
        }
    }

    // TODO: Support test for pluginVersion 1.4.0+
    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.3.30 <=> 1.3.72")
    fun testJvmWithJavaOnHMPP() {
        configureByFiles()
        importProject()

        checkProjectStructure(true, false, true) {
            allModules { assertNoDependencyInBuildClasses() }

            module("jvm-on-mpp")

            module("jvm-on-mpp.jvm-mod")

            module("jvm-on-mpp.jvm-mod.main") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmMain", DependencyScope.COMPILE)
            }

            module("jvm-on-mpp.jvm-mod.test") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.jvm-mod.main", DependencyScope.COMPILE)
            }

            module("jvm-on-mpp.hmpp-mod-a")

            module("jvm-on-mpp.hmpp-mod-a.commonMain")

            module("jvm-on-mpp.hmpp-mod-a.commonTest") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.TEST)
            }

            module("jvm-on-mpp.hmpp-mod-a.jsMain") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsMain", DependencyScope.COMPILE)
            }

            module("jvm-on-mpp.hmpp-mod-a.jsTest") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jsMain", DependencyScope.TEST)
                moduleDependency(
                    "jvm-on-mpp.hmpp-mod-a.jsMain",
                    DependencyScope.RUNTIME
                ) // Temporary dependency, need to remove after KT-40551 is solved
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsTest", DependencyScope.TEST)
            }

            module("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
            }

            module("jvm-on-mpp.hmpp-mod-a.jvmAndJsTest") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.TEST)
            }

            module("jvm-on-mpp.hmpp-mod-a.jvmMain") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.COMPILE)
            }

            module("jvm-on-mpp.hmpp-mod-a.jvmTest") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmAndJsTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.jvmMain", DependencyScope.TEST)
                moduleDependency(
                    "jvm-on-mpp.hmpp-mod-a.jvmMain",
                    DependencyScope.RUNTIME
                )  // Temporary dependency, need to remove after KT-40551 is solved
            }

            module("jvm-on-mpp.hmpp-mod-a.linuxAndJsMain") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
            }

            module("jvm-on-mpp.hmpp-mod-a.linuxAndJsTest") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsMain", DependencyScope.TEST)
            }

            module("jvm-on-mpp.hmpp-mod-a.linuxX64Main") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.COMPILE)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsMain", DependencyScope.COMPILE)

                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)

                if (kotlinPluginVersion >= parseKotlinVersion("1.4.0")) {
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64",
                        DependencyScope.PROVIDED
                    )
                } else {
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - builtin",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - iconv",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - linux",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - posix",
                        DependencyScope.PROVIDED
                    )
                    libraryDependency(
                        "Kotlin/Native ${kotlinPluginVersionString} - zlib",
                        DependencyScope.PROVIDED
                    )
                }
            }

            module("jvm-on-mpp.hmpp-mod-a.linuxX64Test") {
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.commonTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsMain", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxAndJsTest", DependencyScope.TEST)
                moduleDependency("jvm-on-mpp.hmpp-mod-a.linuxX64Main", DependencyScope.TEST)

                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)

                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - builtin( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - iconv( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - linux( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - posix( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
                libraryDependency(
                    Regex("Kotlin/Native ${kotlinPluginVersionString} - zlib( \\| linux_x64)?"),
                    DependencyScope.PROVIDED
                )
            }

            module("jvm-on-mpp.hmpp-mod-a.main")
            module("jvm-on-mpp.hmpp-mod-a.test")
        }
    }


    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.20+")
    fun testJvmAndAndroidMainCoroutinesDependency() {
        configureByFiles()
        createLocalPropertiesSubFileForAndroid()
        importProject()
        val highlightingCheck = createHighlightingCheck()
        checkProjectStructure(false, false, false) {
            allModules {
                assertNoDependencyInBuildClasses()
                highlightingCheck(module)
            }

            module("project.p1.commonMain") {
                libraryDependency(
                    "Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-metadata:commonMain:1.4.2",
                    DependencyScope.COMPILE
                )
            }

            module("project.p1.jvmAndAndroidMain") {
                libraryDependency(
                    "Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-metadata:commonMain:1.4.2",
                    DependencyScope.COMPILE
                )

                if (kotlinPluginVersion < parseKotlinVersion("1.5.20-dev")) {
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-metadata:concurrentMain:1.4.2",
                        DependencyScope.COMPILE
                    )
                }

                libraryDependency(
                    "Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.4.2",
                    DependencyScope.COMPILE
                )

                if (kotlinPluginVersion < parseKotlinVersion("1.4.30")) {
                    libraryDependency(
                        "Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-metadata:all:1.4.2",
                        DependencyScope.COMPILE
                    )
                }
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testPrecisePlatformsHmpp() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val anyNative = NativePlatforms.unspecifiedNativePlatform
        val linux = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64)
        val macos = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)

        checkProjectStructure(exhaustiveModuleList = true, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }
            module("my-app") {
                targetPlatform(jvm)
            }

            module("my-app.commonMain") { targetPlatform(jvm, anyNative) }
            module("my-app.commonTest") { targetPlatform(jvm, anyNative) }

            module("my-app.jvmAndLinuxMain") { targetPlatform(jvm, anyNative) }
            module("my-app.jvmAndLinuxTest") { targetPlatform(jvm, anyNative) }

            module("my-app.jvmMain") { targetPlatform(jvm) }
            module("my-app.jvmTest") { targetPlatform(jvm) }

            module("my-app.linuxX64Main") { targetPlatform(linux) }
            module("my-app.linuxX64Test") { targetPlatform(linux) }

            module("my-app.macosX64Main") { targetPlatform(macos) }
            module("my-app.macosX64Test") { targetPlatform(macos) }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testPrecisePlatformsWithUnrelatedModuleHmpp() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val anyNative = NativePlatforms.unspecifiedNativePlatform
        val js = JsPlatforms.defaultJsPlatform
        val linux = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64)
        val macos = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)

        checkProjectStructure(exhaustiveModuleList = true, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }
            // root project
            module("my-app") {
                targetPlatform(jvm)
            }

            module("my-app.commonMain") { targetPlatform(jvm, anyNative) }
            module("my-app.commonTest") { targetPlatform(jvm, anyNative) }

            module("my-app.jvmAndLinuxMain") { targetPlatform(jvm, anyNative) }
            module("my-app.jvmAndLinuxTest") { targetPlatform(jvm, anyNative) }

            module("my-app.jvmMain") { targetPlatform(jvm) }
            module("my-app.jvmTest") { targetPlatform(jvm) }

            module("my-app.linuxX64Main") { targetPlatform(linux) }
            module("my-app.linuxX64Test") { targetPlatform(linux) }

            module("my-app.macosX64Main") { targetPlatform(macos) }
            module("my-app.macosX64Test") { targetPlatform(macos) }


            // submodule
            module("my-app.submodule") { targetPlatform(jvm) }

            module("my-app.submodule.commonMain") { targetPlatform(js, jvm) }
            module("my-app.submodule.commonTest") { targetPlatform(js, jvm) }

            module("my-app.submodule.jvmMain") { targetPlatform(jvm) }
            module("my-app.submodule.jvmTest") { targetPlatform(jvm) }

            module("my-app.submodule.jsMain") { targetPlatform(js) }
            module("my-app.submodule.jsTest") { targetPlatform(js) }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testOrphanSourceSet() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val js = JsPlatforms.defaultJsPlatform

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }

            module("my-app.commonMain") {
                // must not be (jvm, js, native)
                targetPlatform(jvm, js)
            }

            module("my-app.orphan") {
                targetPlatform(jvm, js)
            }

            module("my-app") {
                assertDiagnosticsCount<OrphanSourceSetsImportingDiagnostic>(1)
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testSourceSetIncludedIntoCompilationDirectly() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val js = JsPlatforms.defaultJsPlatform

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }
            module("my-app.commonMain") {
                targetPlatform(jvm, js) // must not be (jvm, js, native)
            }

            module("my-app.includedIntoJvm") {
                targetPlatform(jvm) // !
            }

            module("my-app.includedIntoJvmAndJs") {
                targetPlatform(jvm, js) // !
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testDefaultSourceSetDependsOnDefaultSourceSet() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val js = JsPlatforms.defaultJsPlatform

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }
            module("my-app.commonMain") {
                targetPlatform(jvm, js) // must not be (jvm, js, native)
            }

            module("my-app.intermediateBetweenJsAndCommon") {
                targetPlatform(jvm, js) // must not be (jvm, js, native)
            }

            module("my-app.jsMain") {
                targetPlatform(js) // must not be (jvm, js)
            }

            module("my-app.jvmMain") {
                targetPlatform(jvm)
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testDefaultSourceSetIncludedIntoAnotherCompilationDirectly() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val js = JsPlatforms.defaultJsPlatform

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }

            module("my-app.jvmMain") {
                targetPlatform(jvm) // must not be (jvm, js)
            }

            module("my-app.jsMain") {
                targetPlatform(js)
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testSourceSetsWithDependsOnButNotIncludedIntoCompilation() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform
        val js = JsPlatforms.defaultJsPlatform

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }

            module("my-app") {
                assertDiagnosticsCount<OrphanSourceSetsImportingDiagnostic>(3)
            }

            // (jvm, js, native) is highly undesirable
            module("my-app.danglingOnJvm") {
                targetPlatform(jvm, js)
            }

            module("my-app.commonMain") {
                targetPlatform(jvm, js)
            }

            module("my-app.danglingOnCommon") {
                targetPlatform(jvm, js)
            }

            module("my-app.danglingOnJvmAndJs") {
                targetPlatform(jvm, js)
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testCustomAddToCompilationPlusDependsOn() {
        configureByFiles()
        importProject()

        val jvm = JvmPlatforms.defaultJvmPlatform

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }

            module("my-app.includedIntoJvm") {
                targetPlatform(jvm) // !
            }

            module("my-app.pseudoOrphan") {
                targetPlatform(jvm) // !
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.4.0+")
    fun testCommonMainIsSingleBackend() {
        configureByFiles()
        importProject()

        val macosX64 = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
        val linuxX64 = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64)

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
            allModules { assertNoDependencyInBuildClasses() }

            module("my-app.commonMain") { targetPlatform(macosX64, linuxX64) }
            module("my-app.commonTest") { targetPlatform(macosX64, linuxX64) }

            module("my-app.linuxX64Main") { targetPlatform(linuxX64) }
            module("my-app.linuxX64Test") { targetPlatform(linuxX64) }

            module("my-app.macosX64Test") { targetPlatform(macosX64) }
            module("my-app.macosX64Main") { targetPlatform(macosX64) }
        }
    }


    @Test
    @PluginTargetVersions(gradleVersion = "5.6+", pluginVersion = "1.3.72+")
    fun testKTIJ1215JvmTestToJvmMainDependency() {
        configureByFiles()
        createLocalPropertiesSubFileForAndroid()
        importProject()
        checkProjectStructure(
            exhaustiveModuleList = false,
            exhaustiveSourceSourceRootList = false,
            exhaustiveDependencyList = false
        ) {
            /* Assert that we do not depend on any classes from the build directory */
            allModules {
                isHMPP(true)
                assertNoDependencyInBuildClasses()
            }
            module("project.p2.commonMain")
            module("project.p2.commonTest")
            module("project.p2.jvmMain")
            module("project.p2.jvmTest") {
                moduleDependency("project.p2.commonMain", DependencyScope.TEST)
                moduleDependency("project.p2.commonTest", DependencyScope.TEST)
                moduleDependency("project.p2.jvmMain", DependencyScope.TEST)
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "6.1+", pluginVersion = "1.4+")
    fun testKT46417NativePlatformTestSourceSets() {
        // Regression in 1.5.0
        if (kotlinPluginVersion == KotlinVersion(1, 5, 0)) {
            isSdkCreationCheckerSuppressed = true
            throw AssumptionViolatedException("1.5.0 is not supported: https://youtrack.jetbrains.com/issue/KT-46417")
        }

        configureByFiles()
        importProject()

        checkProjectStructure(false, false, false) {
            module("project.p2.iosTest") {
                /* Intra project dependencies */
                moduleDependency("project.p2.commonTest", DependencyScope.TEST)
                moduleDependency("project.p2.commonMain", DependencyScope.TEST)
                moduleDependency("project.p2.iosMain", DependencyScope.TEST)

                /* Dependencies on p1 */
                moduleDependency("project.p1.commonMain", DependencyScope.TEST)
                moduleDependency("project.p1.nativeMain", DependencyScope.TEST)
                moduleDependency("project.p1.iosMain", DependencyScope.TEST)

                assertExhaustiveModuleDependencyList()
            }

            module("project.p2.iosX64Test") {
                /* Intra project dependencies */
                moduleDependency("project.p2.commonTest", DependencyScope.TEST)
                moduleDependency("project.p2.commonMain", DependencyScope.TEST)
                moduleDependency("project.p2.iosMain", DependencyScope.TEST)
                moduleDependency("project.p2.iosX64Main", DependencyScope.TEST)
                moduleDependency("project.p2.iosTest", DependencyScope.TEST)

                /* Dependencies on p1 */
                moduleDependency("project.p1.commonMain", DependencyScope.TEST)
                moduleDependency("project.p1.nativeMain", DependencyScope.TEST)
                moduleDependency("project.p1.iosMain", DependencyScope.TEST)
                moduleDependency(
                    "project.p1.iosX64Main", DependencyScope.TEST,
                    //https://youtrack.jetbrains.com/issue/KTIJ-11578
                    isOptional = !SystemInfo.isMac
                )

                assertExhaustiveModuleDependencyList()
            }

            module("project.p2.iosArm64Test") {
                /* Intra project dependencies */
                moduleDependency("project.p2.commonTest", DependencyScope.TEST)
                moduleDependency("project.p2.commonMain", DependencyScope.TEST)
                moduleDependency("project.p2.iosMain", DependencyScope.TEST)
                moduleDependency("project.p2.iosArm64Main", DependencyScope.TEST)
                moduleDependency("project.p2.iosTest", DependencyScope.TEST)

                /* Dependencies on p1 */
                moduleDependency("project.p1.commonMain", DependencyScope.TEST)
                moduleDependency("project.p1.nativeMain", DependencyScope.TEST)
                moduleDependency("project.p1.iosMain", DependencyScope.TEST)
                moduleDependency(
                    "project.p1.iosArm64Main", DependencyScope.TEST,
                    //https://youtrack.jetbrains.com/issue/KTIJ-11578
                    isOptional = !SystemInfo.isMac
                )

                assertExhaustiveModuleDependencyList()
            }


            module("project.p2.linuxX64Test") {
                /* Intra project dependencies */
                moduleDependency("project.p2.commonTest", DependencyScope.TEST)
                moduleDependency("project.p2.commonMain", DependencyScope.TEST)
                moduleDependency("project.p2.linuxMain", DependencyScope.TEST)
                moduleDependency("project.p2.linuxX64Main", DependencyScope.TEST)


                /* Dependencies on p1 */
                moduleDependency("project.p1.commonMain", DependencyScope.TEST)
                moduleDependency("project.p1.nativeMain", DependencyScope.TEST)
                moduleDependency("project.p1.linuxMain", DependencyScope.TEST)
                moduleDependency("project.p1.linuxX64Main", DependencyScope.TEST)

                assertExhaustiveModuleDependencyList()
            }

            module("project.p2.linuxArm64Test") {
                /* Intra project dependencies */
                moduleDependency("project.p2.commonTest", DependencyScope.TEST)
                moduleDependency("project.p2.commonMain", DependencyScope.TEST)
                moduleDependency("project.p2.linuxMain", DependencyScope.TEST)
                moduleDependency("project.p2.linuxArm64Main", DependencyScope.TEST)

                /* Dependencies on p1 */
                moduleDependency("project.p1.commonMain", DependencyScope.TEST)
                moduleDependency("project.p1.nativeMain", DependencyScope.TEST)
                moduleDependency("project.p1.linuxMain", DependencyScope.TEST)
                moduleDependency("project.p1.linuxArm64Main", DependencyScope.TEST)

                assertExhaustiveModuleDependencyList()
            }


            createHighlightingCheck(
                testLineMarkers = false,
                severityLevel = HighlightSeverity.ERROR
            ).let { highlightingCheck ->
                ModuleManager.getInstance(myProject).modules.forEach { module ->
                    // https://youtrack.jetbrains.com/issue/KTIJ-11578
                    if (module.name.contains("iosArm64") || module.name.contains("iosX64")) {
                        if (SystemInfo.isMac) {
                            highlightingCheck(module)
                        }
                    } else {
                        highlightingCheck(module)
                    }
                }
            }
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.0-dev+")
    fun testDependencyOnStdlibFromPlatformSourceSets() {
        configureByFiles()
        importProject()

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = true) {
            module("my-app.jvmMain") {
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.COMPILE)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinPluginVersionString}", DependencyScope.COMPILE)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinPluginVersionString}", DependencyScope.COMPILE)
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.COMPILE)
                assertExhaustiveDependencyList()
            }

            module("my-app.jvmTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.RUNTIME)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.TEST)
                noLibraryDependency(".*stdlib-common.*")
                assertExhaustiveDependencyList()
            }

            module("my-app.jsMain") {
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}", DependencyScope.COMPILE)
            }

            module("my-app.jsTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.RUNTIME)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}", DependencyScope.TEST)
            }

            module("my-app.linuxX64Main") {
                moduleDependency("my-app.commonMain", DependencyScope.COMPILE)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64", DependencyScope.PROVIDED)
            }

            module("my-app.linuxX64Test") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxX64Main", DependencyScope.TEST)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64", DependencyScope.PROVIDED)
            }
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.20-dev+")
    fun testDependencyOnKotlinTestFromPlatformSourceSets() {
        configureByFiles()
        importProject()

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = true) {
            module("my-app.jvmTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.RUNTIME)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.TEST)
                libraryDependency("Gradle: junit:junit:4.13.2", DependencyScope.TEST)
                libraryDependency("Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-test-junit:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-test:${kotlinPluginVersionString}", DependencyScope.TEST)
            }

            module("my-app.jsTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.RUNTIME)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-test-js:${kotlinPluginVersionString}", DependencyScope.TEST)
            }

            module("my-app.linuxX64Test") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxX64Main", DependencyScope.TEST)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64", DependencyScope.PROVIDED)
            }
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.20-dev+")
    fun testMppLibAndHmppConsumer() {
        configureByFiles()

        runTaskAndGetErrorOutput("$projectPath/lib", "publish")

        importProject()

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = true) {
            module("my-app.jvmTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.RUNTIME)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.TEST)
                // BAD! HMPP consumer gets dependency on root module of pre-HMPP library in platform-specific source set
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-jvm:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.TEST)
            }

            module("my-app.jsTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.RUNTIME)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-js:1.0", DependencyScope.TEST)
            }

            module("my-app.linuxX64Test") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxX64Main", DependencyScope.TEST)
                // BAD! HMPP consumer gets dependency on root module of pre-HMPP library in platform-specific source set
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-linuxx64:klib:1.0", DependencyScope.TEST)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64", DependencyScope.PROVIDED)
            }
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.20-dev+")
    fun testHmppLibAndHmppConsumer() {
        configureByFiles()

        runTaskAndGetErrorOutput("$projectPath/lib", "publish")

        importProject()

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = true) {
            module("my-app.jvmTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.RUNTIME)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.TEST)
                // note: HMPP consumers get only platform module from HMPP libraries, as expected
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-jvm:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.TEST)
            }

            module("my-app.jsTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.RUNTIME)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}", DependencyScope.TEST)
                // note: HMPP consumers get only platform module from HMPP libraries, as expected
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-js:1.0", DependencyScope.TEST)
            }

            module("my-app.linuxX64Test") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxX64Main", DependencyScope.TEST)
                // note: HMPP consumers get only platform module from HMPP libraries, as expected
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-linuxx64:klib:1.0", DependencyScope.TEST)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64", DependencyScope.PROVIDED)
            }
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.20-dev+")
    fun testHmppLibAndMppConsumer() {
        configureByFiles()

        runTaskAndGetErrorOutput("$projectPath/lib", "publish")

        importProject()

        checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = true) {
            module("my-app.jvmTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.TEST)
                moduleDependency("my-app.jvmMain", DependencyScope.RUNTIME)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib:${kotlinPluginVersionString}", DependencyScope.TEST)
                // pre-HMPP consumers get dependency on common module, this is fine because without HMPP platform-specific
                // source set won't be able to read .kotlin_metadata even
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-jvm:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: org.jetbrains:annotations:13.0", DependencyScope.TEST)
            }

            module("my-app.jsTest") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.TEST)
                moduleDependency("my-app.jsMain", DependencyScope.RUNTIME)
                libraryDependency("Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:${kotlinPluginVersionString}", DependencyScope.TEST)
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-js:1.0", DependencyScope.TEST)
            }

            module("my-app.linuxX64Test") {
                moduleDependency("my-app.commonTest", DependencyScope.TEST)
                moduleDependency("my-app.commonMain", DependencyScope.TEST)
                moduleDependency("my-app.linuxX64Main", DependencyScope.TEST)
                // pre-HMPP consumers get dependency on common module, this is fine because without HMPP platform-specific
                // source set won't be able to read .kotlin_metadata even
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib:1.0", DependencyScope.TEST)
                libraryDependency("Gradle: com.h0tk3y.mpp.demo:lib-linuxx64:klib:1.0", DependencyScope.TEST)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - builtin | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - iconv | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - linux | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - posix | linux_x64", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - stdlib", DependencyScope.PROVIDED)
                libraryDependency("Kotlin/Native ${kotlinPluginVersionString} - zlib | linux_x64", DependencyScope.PROVIDED)
            }
        }
    }

    override fun createImportSpec(): ImportSpec {
        return ImportSpecBuilder(super.createImportSpec())
            .createDirectoriesForEmptyContentRoots()
            .build()
    }

    override fun importProject() {
        val isUseQualifiedModuleNames = currentExternalProjectSettings.isUseQualifiedModuleNames
        currentExternalProjectSettings.isUseQualifiedModuleNames = true
        try {
            super.importProject()
        } finally {
            currentExternalProjectSettings.isUseQualifiedModuleNames = isUseQualifiedModuleNames
        }
    }

    override fun clearTextFromMarkup(text: String): String {
        return clearTextFromDiagnosticMarkup(text)
    }


    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)

    override fun testDataDirName(): String {
        return "hierarchicalMultiplatformImport"
    }
}
