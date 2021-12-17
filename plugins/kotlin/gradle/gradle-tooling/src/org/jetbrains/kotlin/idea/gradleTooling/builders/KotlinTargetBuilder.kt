// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetJarReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.idea.projectModel.KotlinTestRunTask

object KotlinTargetBuilder : KotlinMultiplatformComponentBuilder<KotlinTarget> {
    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KotlinTarget? {
        val kotlinTargetReflection = KotlinTargetReflection(origin)
        /* Loading class safely to still support Kotlin Gradle Plugin 1.3.30 */
        if (kotlinTargetReflection.isMetadataTargetClass) return null

        val name = kotlinTargetReflection.targetName ?: return null
        val platformId = kotlinTargetReflection.platformType ?: return null
        val platform = KotlinPlatform.byId(platformId) ?: return null
        val disambiguationClassifier = kotlinTargetReflection.disambiguationClassifier ?: return null
        val targetPresetName: String? = kotlinTargetReflection.presetName

        val compilations = kotlinTargetReflection.compilations?.mapNotNull {
            KotlinCompilationBuilder(platform, disambiguationClassifier).buildComponent(it, importingContext)
        } ?: return null
        val jar = kotlinTargetReflection.artifactsTaskName
            ?.let { importingContext.project.tasks.findByName(it) }
            ?.let { KotlinTargetJarReflection(it) }
            ?.let { KotlinTargetJarImpl(it.archiveFile) }
        val nativeMainRunTasks =
            if (platform == KotlinPlatform.NATIVE) kotlinTargetReflection.nativeMainRunTasks.orEmpty().mapNotNull { nativeMainRunReflection ->
                val taskName = nativeMainRunReflection.taskName ?: return@mapNotNull null
                val compilationName = nativeMainRunReflection.compilationName ?: return@mapNotNull null
                val entryPoint = nativeMainRunReflection.entryPoint ?: return@mapNotNull null
                val debuggable = nativeMainRunReflection.debuggable ?: return@mapNotNull null
                KotlinNativeMainRunTaskImpl(taskName, compilationName, entryPoint, debuggable)
            }
            else emptyList()

        val artifacts = kotlinTargetReflection.konanArtifacts?.mapNotNull {
            KonanArtifactModelBuilder.buildComponent(it, importingContext)
        } .orEmpty()

        val testRunTasks =  buildTestRunTasks(importingContext.project, origin as Named)
        val target = KotlinTargetImpl(
            name,
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

    private fun Named.testTaskClass(className: String) = try {
        // This is a workaround that makes assumptions about the tasks naming logic
        // and is therefore an unstable and temporary solution until test runs API is implemented:
        @Suppress("UNCHECKED_CAST")
        this.javaClass.classLoader.loadClass(className) as Class<out Task>
    } catch (_: ClassNotFoundException) {
        null
    }

    private fun buildTestRunTasks(project: Project, gradleTarget: Named): Collection<KotlinTestRunTask> {
        val getTestRunsMethod = gradleTarget.javaClass.getMethodOrNull("getTestRuns")
        if (getTestRunsMethod != null) {
            val testRuns = getTestRunsMethod.invoke(gradleTarget) as? Iterable<Any>
            if (testRuns != null) {
                val testReports =
                    testRuns.mapNotNull { (it.javaClass.getMethodOrNull("getExecutionTask")?.invoke(it) as? TaskProvider<Task>)?.get() }
                val testTasks = testReports.flatMap {
                    ((it.javaClass.getMethodOrNull("getTestTasks")?.invoke(it) as? Collection<Any>)?.mapNotNull {
                        //TODO(auskov): getTestTasks should return collection of TaskProviders without mixing with Tasks
                        when (it) {
                            is Provider<*> -> it.get() as? Task
                            is Task -> it
                            else -> null
                        }
                    }) ?: listOf(it)
                }
                return testTasks.filter { it.enabled }.map {
                    val name = it.name
                    val compilation = it.javaClass.getMethodOrNull("getCompilation")?.invoke(it)
                    val compilationName = compilation?.javaClass?.getMethodOrNull("getCompilationName")?.invoke(compilation)?.toString()
                        ?: KotlinCompilation.TEST_COMPILATION_NAME
                    KotlinTestRunTaskImpl(name, compilationName)
                }.toList()
            }
            return emptyList()
        }

        // Otherwise, find the Kotlin/JVM test task with names matching the target name or
        // aggregate Android JVM tasks (like testDebugUnitTest).
        val kotlinTestTaskClass = gradleTarget.testTaskClass("org.jetbrains.kotlin.gradle.tasks.KotlinTest") ?: return emptyList()

        val targetDisambiguationClassifier = run {
            val getDisambiguationClassifier = gradleTarget.javaClass.getMethodOrNull("getDisambiguationClassifier")
                ?: return emptyList()

            getDisambiguationClassifier(gradleTarget) as String?
        }

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
                targetDisambiguationClassifier.isNullOrEmpty() ||
                        testTaskDisambiguationClassifier != null &&
                        testTaskDisambiguationClassifier.startsWith(targetDisambiguationClassifier.orEmpty())
            }
        }.map { KotlinTestRunTaskImpl(it, KotlinCompilation.TEST_COMPILATION_NAME) }
    }
}