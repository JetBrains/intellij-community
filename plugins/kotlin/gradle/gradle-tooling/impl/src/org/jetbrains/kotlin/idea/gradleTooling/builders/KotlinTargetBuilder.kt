// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetJarReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetReflection
import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinExtras
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.idea.projectModel.KotlinTestRunTask
import org.jetbrains.kotlin.tooling.core.closure

object KotlinTargetBuilder : KotlinMultiplatformComponentBuilder<KotlinTargetReflection, KotlinTarget> {
    override fun buildComponent(origin: KotlinTargetReflection, importingContext: MultiplatformModelImportingContext): KotlinTarget? {
        /* Loading class safely to still support Kotlin Gradle Plugin 1.3.30 */
        if (origin.isMetadataTargetClass) return null

        val name = origin.targetName
        val platformId = origin.platformType ?: return null
        val platform = KotlinPlatform.byId(platformId) ?: return null
        val disambiguationClassifier = origin.disambiguationClassifier
        val targetPresetName: String? = origin.presetName

        val reflectionsByCompilations = origin.compilations?.associate { compilationReflection ->
            KotlinCompilationBuilder(platform, disambiguationClassifier).buildComponent(
                compilationReflection,
                importingContext
            ) to compilationReflection
        } ?: return null
        val compilations = reflectionsByCompilations.mapNotNull { it.key }

        val testRunTasks = buildTestRunTasks(importingContext.project, origin)

        val nativeMainRunTasks =
            if (platform == KotlinPlatform.NATIVE) origin.nativeMainRunTasks.orEmpty().mapNotNull { nativeMainRunReflection ->
                val taskName = nativeMainRunReflection.taskName ?: return@mapNotNull null
                val compilationName = nativeMainRunReflection.compilationName ?: return@mapNotNull null
                val entryPoint = nativeMainRunReflection.entryPoint ?: return@mapNotNull null
                val debuggable = nativeMainRunReflection.debuggable ?: return@mapNotNull null
                KotlinNativeMainRunTaskImpl(taskName, compilationName, entryPoint, debuggable)
            }
            else emptyList()

        val artifactTask = origin.artifactsTaskName?.let { importingContext.project.tasks.findByName(it) }

        val jar = artifactTask?.let { KotlinTargetJarReflection(it) }?.let { jarReflection ->
            val compileKotlinTaskNames = origin.compilations?.map { it.compileKotlinTaskName }?.toSet().orEmpty()
            val taskDependenciesClosureFromThisProject = artifactTask.closure { task ->
                // getDependencies may throw in case of project misconfiguration, consider task dependencies empty in this case
                runCatching { task.taskDependencies.getDependencies(task) }.getOrNull().orEmpty()
                    .filter { dependencyTask -> dependencyTask.project == artifactTask.project }
            }
            val dependencyCompilationTaskNames = taskDependenciesClosureFromThisProject.map(Task::getName)
                .filter { taskName -> taskName in compileKotlinTaskNames }
                .toSet()
            val compilationsOfJarTask = compilations.filter {
                reflectionsByCompilations[it]?.compileKotlinTaskName.orEmpty() in dependencyCompilationTaskNames
            }
            KotlinTargetJarImpl(jarReflection.archiveFile, compilationsOfJarTask)
        }

        val artifacts = origin.konanArtifacts?.mapNotNull {
            KonanArtifactModelBuilder.buildComponent(it, importingContext)
        }.orEmpty()

        val serializedExtras = importingContext.importReflection?.resolveExtrasSerialized(origin.gradleTarget)

        val target = KotlinTargetImpl(
            name,
            targetPresetName,
            disambiguationClassifier,
            platform,
            compilations,
            testRunTasks,
            nativeMainRunTasks,
            jar,
            artifacts,
            IdeaKotlinExtras.from(serializedExtras)
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

    @Suppress("UNCHECKED_CAST")
    private fun buildTestRunTasks(project: Project, target: KotlinTargetReflection): Collection<KotlinTestRunTask> {
        val gradleTarget = target.gradleTarget
        val getTestRunsMethod = gradleTarget.javaClass.getMethodOrNull("getTestRuns")
        if (getTestRunsMethod != null) {
            val testRuns = getTestRunsMethod.invoke(gradleTarget) as? Iterable<Any>
            if (testRuns != null) {
                val testReports =
                    testRuns.mapNotNull { (it.javaClass.getMethodOrNull("getExecutionTask")?.invoke(it) as? TaskProvider<Task>)?.get() }
                val testTasks = testReports.flatMap {
                    ((it.javaClass.getMethodOrNull("getTestTasks")?.invoke(it) as? Collection<Any>)?.mapNotNull { testTask ->
                        //TODO(auskov): getTestTasks should return collection of TaskProviders without mixing with Tasks
                        when (testTask) {
                            is Provider<*> -> testTask.get() as? Task
                            is Task -> testTask
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

        // The 'targetName' of a test task matches the target disambiguation classifier, potentially with suffix, e.g. jsBrowser
        val getTargetName = kotlinTestTaskClass.getDeclaredMethodOrNull("getTargetName") ?: return emptyList()

        val jvmTestTaskClass =
            gradleTarget.testTaskClass("org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest") ?: return emptyList()
        val getJvmTargetName = jvmTestTaskClass.getDeclaredMethodOrNull("getTargetName") ?: return emptyList()

        /**
         * Only run this branch for the 'KotlinAndroidTarget'  maintained in KGP:
         * This target can be identified checking the 'presetName' here.
         * The External Android target will not have any presetName as it is using the external target API instead of presets.
         */
        if (target.presetName ==  "android") {
            val androidUnitTestClass = gradleTarget.testTaskClass("com.android.build.gradle.tasks.factory.AndroidUnitTest")
                ?: return emptyList()

            return project.tasks.filter { androidUnitTestClass.isInstance(it) }.mapNotNull { task -> task.name }
                .map { KotlinTestRunTaskImpl(it, KotlinCompilation.TEST_COMPILATION_NAME) }
        }

        return project.tasks.filter { kotlinTestTaskClass.isInstance(it) || jvmTestTaskClass.isInstance(it) }.mapNotNull { task ->
            val testTaskDisambiguationClassifier =
                (if (kotlinTestTaskClass.isInstance(task)) getTargetName(task) else getJvmTargetName(task)) as String?
            task.name.takeIf {
                target.disambiguationClassifier.isNullOrEmpty() ||
                        testTaskDisambiguationClassifier != null &&
                        testTaskDisambiguationClassifier.startsWith(target.disambiguationClassifier.orEmpty())
            }
        }.map { KotlinTestRunTaskImpl(it, KotlinCompilation.TEST_COMPILATION_NAME) }
    }
}