// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.*
import java.io.File

object KotlinTargetBuilder : KotlinMultiplatformComponentBuilder<KotlinTarget> {
    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KotlinTarget? {
        val gradleTarget = origin as Named

        val targetClass = gradleTarget.javaClass

        /* Loading class safely to still support Kotlin Gradle Plugin 1.3.30 */
        val metadataTargetClass = targetClass.classLoader.loadClassOrNull("org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget")
        if (metadataTargetClass?.isInstance(gradleTarget) == true) return null

        val platformId = (gradleTarget["getPlatformType"] as? Named)?.name ?: return null
        val platform = KotlinPlatform.byId(platformId) ?: return null
        val useDisambiguationClassifier = gradleTarget["getUseDisambiguationClassifierAsSourceSetNamePrefix"] as? Boolean ?: true
        val disambiguationClassifier = if (useDisambiguationClassifier)
            gradleTarget["getDisambiguationClassifier"] as? String
        else {
            gradleTarget["getOverrideDisambiguationClassifierOnIdeImport"] as? String
        }
        val targetPresetName: String? = try {
            gradleTarget["getPreset"]["getName"] as? String
        } catch (e: Throwable) {
            "${e::class.java.name}:${e.message}"
        }

        val gradleCompilations = gradleTarget.compilations ?: return null
        val compilations = gradleCompilations.mapNotNull {
            KotlinCompilationBuilder(platform, disambiguationClassifier).buildComponent(it, importingContext)
        }
        val jar = buildTargetJar(gradleTarget, importingContext.project)
        val testRunTasks = buildTestRunTasks(importingContext.project, gradleTarget)
        val nativeMainRunTasks =
            if (platform == KotlinPlatform.NATIVE) buildNativeMainRunTasks(gradleTarget)
            else emptyList()
        val artifacts = konanArtifacts(gradleTarget, importingContext)
        val target = KotlinTargetImpl(
            gradleTarget.name,
            targetPresetName,
            disambiguationClassifier,
            platform,
            compilations,
            testRunTasks,
            nativeMainRunTasks,
            jar,
            artifacts
        )
        compilations.forEach {
            it.disambiguationClassifier = target.disambiguationClassifier
            it.platform = target.platform
        }
        return target
    }

    private fun konanArtifacts(target: Named, importingContext: MultiplatformModelImportingContext): List<KonanArtifactModel> =
        (target["getBinaries"] as? Collection<Any?>)
            ?.filterNotNull()
            ?.mapNotNull { KonanArtifactModelBuilder.buildComponent(it, importingContext) }
            ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun buildNativeMainRunTasks(gradleTarget: Named): Collection<KotlinNativeMainRunTask> {
        val executableBinaries = (gradleTarget["getBinaries"] as? Collection<Any>)
            ?.filter { it.javaClass.name == "org.jetbrains.kotlin.gradle.plugin.mpp.Executable" }
            ?: return emptyList()
        return executableBinaries.mapNotNull { binary ->
            val runTaskName = binary["getRunTaskName"] as String? ?: return@mapNotNull null
            val entryPoint = binary["getEntryPoint"] as String? ?: return@mapNotNull null
            val debuggable = binary["getDebuggable"] as Boolean

            val compilationName = binary["getCompilation"]?.let {
                it["getCompilationName"]?.toString()
            } ?: KotlinCompilation.MAIN_COMPILATION_NAME

            KotlinNativeMainRunTaskImpl(
                runTaskName,
                compilationName,
                entryPoint,
                debuggable
            )
        }
    }

    private fun Named.testTaskClass(className: String) = try {
        // This is a workaround that makes assumptions about the tasks naming logic
        // and is therefore an unstable and temporary solution until test runs API is implemented:
        @Suppress("UNCHECKED_CAST")
        this.javaClass.classLoader.loadClass(className) as Class<out Task>
    } catch (_: ClassNotFoundException) {
        null
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTestRunTasks(project: Project, gradleTarget: Named): Collection<KotlinTestRunTask> {
        val getTestRunsMethod = gradleTarget.javaClass.getMethodOrNull("getTestRuns")
        if (getTestRunsMethod != null) {
            val testRuns = getTestRunsMethod.invoke(gradleTarget) as? Iterable<Any>
            if (testRuns != null) {
                val testReports =
                    testRuns.mapNotNull { (it["getExecutionTask"] as? TaskProvider<Task>)?.get() }
                val testTasks = testReports.flatMap {
                    (it["getTestTasks"] as? Collection<Any>)?.mapNotNull {
                        //TODO(auskov): getTestTasks should return collection of TaskProviders without mixing with Tasks
                        when (it) {
                            is Provider<*> -> it.get() as? Task
                            is Task -> it
                            else -> null
                        }
                    } ?: listOf(it)
                }
                return testTasks.filter { it.enabled }.map {
                    val name = it.name
                    val compilationName = it["getCompilation"]?.let { compilation ->
                        compilation["getCompilationName"]?.toString()
                    } ?: KotlinCompilation.TEST_COMPILATION_NAME
                    KotlinTestRunTaskImpl(name, compilationName)
                }.toList()
            }
            return emptyList()
        }

        // Otherwise, find the Kotlin/JVM test task with names matching the target name or
        // aggregate Android JVM tasks (like testDebugUnitTest).
        val kotlinTestTaskClass = gradleTarget.testTaskClass("org.jetbrains.kotlin.gradle.tasks.KotlinTest") ?: return emptyList()

        val targetDisambiguationClassifier = (gradleTarget["getDisambiguationClassifier"] as? String) ?: return emptyList()

        // The 'targetName' of a test task matches the target disambiguation classifier, potentially with suffix, e.g. jsBrowser
        val getTargetName = kotlinTestTaskClass.getDeclaredMethodOrNull("getTargetName") ?: return emptyList()

        val jvmTestTaskClass =
            gradleTarget.testTaskClass("org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest") ?: return emptyList()
        val getJvmTargetName = jvmTestTaskClass.getDeclaredMethodOrNull("getTargetName") ?: return emptyList()

        if (targetDisambiguationClassifier == "android") {
            val androidUnitTestClass = gradleTarget.testTaskClass("com.android.build.gradle.tasks.factory.AndroidUnitTest")
                ?: return emptyList()

            return project.tasks.filter { androidUnitTestClass.isInstance(it) }.mapNotNull { task -> task.name }
                .map { KotlinTestRunTaskImpl(it, KotlinCompilation.TEST_COMPILATION_NAME) }
        }

        return project.tasks.filter { kotlinTestTaskClass.isInstance(it) || jvmTestTaskClass.isInstance(it) }.mapNotNull { task ->
            val testTaskDisambiguationClassifier =
                (if (kotlinTestTaskClass.isInstance(task)) getTargetName(task) else getJvmTargetName(task)) as String?
            task.name.takeIf {
                targetDisambiguationClassifier.isEmpty() ||
                        testTaskDisambiguationClassifier != null &&
                        testTaskDisambiguationClassifier.startsWith(targetDisambiguationClassifier)
            }
        }.map { KotlinTestRunTaskImpl(it, KotlinCompilation.TEST_COMPILATION_NAME) }
    }

    private fun buildTargetJar(gradleTarget: Named, project: Project): KotlinTargetJar? {
        val artifactsTaskName = gradleTarget["getArtifactsTaskName"] as? String ?: return null
        val jarTask = project.tasks.findByName(artifactsTaskName) ?: return null
        val archiveFile = jarTask["getArchivePath"] as? File?
        return KotlinTargetJarImpl(archiveFile)
    }
}