package convention.multiplatform

import convention.common.utils.Config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MultiplatformPlugin : Plugin<Project> {
    override fun <!LINE_MARKER("descr='Implements function in Plugin (org.gradle.api) Press ... to navigate'")!>apply<!>(project: Project) {
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")

        project.extensions.create("multiplatformOptions", MultiplatformOptionsExtension::class.java)

        project.afterEvaluate {
            val options = project.extensions.getByType(MultiplatformOptionsExtension::class.java)
            project.configureMultiplatform(options)
        }
    }
}

private fun Project.configureMultiplatform(options: MultiplatformOptionsExtension) {
    extensions.configure<KotlinMultiplatformExtension> {
        val (jvm, linux, iOS, tvOS, macOS, watchOS, windows) = options

        explicitApi()

        if (jvm) jvm {
            compilerOptions {
                freeCompilerArgs.addAll(Config.jvmCompilerArgs)
            }
        }

        if (linux) {
            linuxX64()
            linuxArm64()
        }

        if (windows) mingwX64()

        sequence {
            if (iOS) {
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(iosArm64())
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(iosSimulatorArm64())
            }
            if (macOS) {
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(macosArm64())
            }
            if (tvOS) {
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(tvosArm64())
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(tvosSimulatorArm64())
            }
            if (watchOS) {
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(watchosArm64())
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(watchosDeviceArm64())
                <!LINE_MARKER("descr='Suspend function call 'yield()''")!>yield<!>(watchosSimulatorArm64())
            }
        }

        sourceSets.all {
            languageSettings {
                progressiveMode = true
                Config.optIns.forEach { optIn(it) }
            }
        }

        targets.all {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.addAll(Config.compilerArgs)
                        optIn.addAll(Config.optIns)
                        progressiveMode.set(true)
                    }
                }
            }
        }
    }
}
