// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.psi.KtNamedFunction
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
        val function = location.psiElement.parentOfType<KtNamedFunction>(withSelf = true) ?: return false
        if (!KotlinMainFunctionDetector.getInstance().isMain(function)) return false
        val runTask = KotlinJvmRunTaskData.findSuitableKotlinJvmRunTask(module) ?: return false
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
        val function = sourceElement.get()?.parentOfType<KtNamedFunction>(withSelf = true) ?: return false
        if (!KotlinMainFunctionDetector.getInstance().isMain(function)) return false
        val runTask = KotlinJvmRunTaskData.findSuitableKotlinJvmRunTask(module) ?: return false

        configureKmpJvmRunConfigurationFromMainFunction(configuration, function, runTask, module)

        return true
    }

    private fun Module?.asJvmModule(): Module? =
        takeIf { it?.platform?.any { it is JvmPlatform } == true }
}
