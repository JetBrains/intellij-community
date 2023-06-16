// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.cast
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.tooling.core.withClosure
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * ## Creating 'run configurations' for executing jvm based code in Kotlin Multiplatform projects.
 * Context: https://youtrack.jetbrains.com/issue/KTIJ-25644/KGP-import-KotlinRunConfiguration-wont-be-able-to-infer-correct-runtime-classpath
 *
 * ### Why is a special run configuration producer needed for Kotlin/JVM in multiplatform projects? (State 05.2023)
 * In order to reduce complexity and work necessary to import Kotlin Multiplatform projects into the IDE, the new
 * implementation (called Kotlin Gradle Plugin based dependency resolution (kgp based)) will only support IDE highlighting
 * when resolving dependencies. Runtime dependencies will not be resolved. This shall increase import performance
 * and simplify the overall Gradle sync.
 *
 * However, the default run configuration producer (for jvm only projects), will just delegate building the project to Gradle
 * and then launches the code from the IDE. This however requires the runtime dependencies to be known.
 *
 * ### The KotlinJvmRun task: 'IDE carrier task'
 * Starting from Kotlin 1.9-Beta, the Kotlin Gradle plugin registers a 'run' task by default for the KotlinJvmTarget:
 * This run task is able to execute arbitrary mainClasses (as instructed by the IDE).
 *
 * #### API contract with Kotlin Gradle Plugin
 * 1) The name of the task will always follow <targetName>Run
 * 2) task will allow passing mainClass using System property using -DmainClass=Foo
 *
 *
 * Using Gradle to also execute the relevant code is generally more desirable from a 'correctness' point of view, as
 * the underlying task can be configured to adhere to the project configuration.
 */
class KotlinMultiplatformJvmRunConfigurationProducer : LazyRunConfigurationProducer<GradleRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        GradleExternalTaskConfigurationType.getInstance().factory

    override fun isConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
        val module = context.module.asJvmModule() ?: return false
        val location = context.location ?: return false
        val function = location.psiElement.parentOfType<KtNamedFunction>() ?: return false
        if (!KotlinMainFunctionDetector.getInstance().isMain(function)) return false
        val runTask = findSuitableKotlinJvmRunTask(module) ?: return false
        if (runTask.taskName !in configuration.settings.taskNames) return false
        return mainClassScriptParameter(function) in configuration.settings.scriptParameters
    }

    override fun setupConfigurationFromContext(
        configuration: GradleRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val module = context.module.asJvmModule() ?: return false
        if (module.isTestModule) return false
        val function = sourceElement.get()?.parentOfType<KtNamedFunction>() ?: return false
        if (!KotlinMainFunctionDetector.getInstance().isMain(function)) return false
        val runTask = findSuitableKotlinJvmRunTask(module) ?: return false

        configuration.name = "${function.containingKtFile.virtualFile.nameWithoutExtension} [${runTask.targetName}]"
        configuration.isDebugAllEnabled = false
        configuration.isDebugServerProcess = false

        configuration.settings.apply {
            externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
            taskNames = listOf(runTask.taskName)
            scriptParameters = "${mainClassScriptParameter(function)} --quiet"
        }

        return true
    }

    private class KotlinJvmRunTaskData(
        val targetName: String, val taskName: String
    )

    /**
     * Will return the *first* suitable KotlinJvmRun task that is suitable for this module.
     * Note: The run gutter will also support running test in common source sets (like commonMain), if those SourceSets
     * will be included in the jvm target offering the run task!
     *
     * Note: There might be more than just the 'first' run task suitable, e.g. in common Source Sets that participate in multiple
     * jvm targets. However, since this is avery advanced use case for now, and is scheduled for deprecation, this case
     * is omitted in order to keep it simple.
     */
    private fun findSuitableKotlinJvmRunTask(module: Module): KotlinJvmRunTaskData? {
        val moduleDataFinder = CachedModuleDataFinder.getInstance(module.project)
        val mainModuleDataNode = moduleDataFinder.findMainModuleData(module) ?: return null

        /* Find all run carrier tasks (tasks implementing KotlinJvmRun */
        val allKotlinJvmRunTasks = ExternalSystemApiUtil.findAll(mainModuleDataNode, ProjectKeys.TASK)
            .filter { it.data.type == KOTLIN_JVM_RUN_CLASS_NAME }
            .ifEmpty { return null }

        /*
        As the passed 'module' can also be a common Source Set (like commonMain),
        We collect all SourceSets that declare a dependsOn as well. If any of those Source Sets can be executed
        by the run task, then the Source Set represented by 'module' can also!
         */
        val sourceSetDataNode = moduleDataFinder.findModuleData(module)?.cast<GradleSourceSetData>() ?: return null
        val allSourceSetDataNodes = ExternalSystemApiUtil.findAll(mainModuleDataNode, GradleSourceSetData.KEY)
        val sourceSetWithDependingSourceSetDataNodes = sourceSetDataNode.withClosure { currentSourceSetDataNode ->
            val currentKotlinSourceSetData = currentSourceSetDataNode.kotlinSourceSetData
            allSourceSetDataNodes.filter { potentialRelevantSourceSetDataNode ->
                val kotlinSourceSetData = potentialRelevantSourceSetDataNode.kotlinSourceSetData ?: return@filter false
                currentKotlinSourceSetData?.sourceSetInfo?.moduleId in kotlinSourceSetData.sourceSetInfo.dependsOn
            }
        }

        /*
        moduleIds of all Source Sets that are associated with the 'module':
        Id of the module, as well as all moduleIds of Source Sets that declared a dependsOn this module.
         */
        val sourceSetModuleIds = sourceSetWithDependingSourceSetDataNodes
            .mapNotNull { it.kotlinSourceSetData?.sourceSetInfo?.moduleId }
            .toSet()

        val allKotlinTargetDataNodes = ExternalSystemApiUtil.findAll(mainModuleDataNode, KotlinTargetData.KEY)

        /*
        Select first runTask, which can includes this 'module'
        1) We ensure the runTask belongs to the target
        2) We ensure that the 'module' belongs to the target
        */
        return allKotlinJvmRunTasks.firstNotNullOfOrNull { runTask ->
            val target = allKotlinTargetDataNodes
                .filter { target -> runTask.data.name.lowercase() == "${target.data.externalName}Run".lowercase() }
                .firstOrNull { target -> target.data.moduleIds.any { targetModuleId -> targetModuleId in sourceSetModuleIds } }
                ?: return@firstNotNullOfOrNull null
            KotlinJvmRunTaskData(target.data.externalName, runTask.data.name)
        }
    }

    private fun mainClassScriptParameter(function: KtFunction): String {
        return "-DmainClass=${function.containingKtFile.javaFileFacadeFqName}"
    }

    private fun Module?.asJvmModule(): Module? =
        takeIf { it?.platform?.any { it is JvmPlatform } == true }

    private companion object {
        const val KOTLIN_JVM_RUN_CLASS_NAME = "org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun"
    }
}
