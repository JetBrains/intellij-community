// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling.model.kapt

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File
import java.io.Serializable
import java.lang.reflect.Modifier

interface KaptSourceSetModel : Serializable {
    val sourceSetName: String
    val isTest: Boolean
    val generatedSourcesDir: String
    val generatedClassesDir: String
    val generatedKotlinSourcesDir: String

    val generatedSourcesDirFile get() = generatedSourcesDir.takeIf { it.isNotEmpty() }?.let(::File)
    val generatedClassesDirFile get() = generatedClassesDir.takeIf { it.isNotEmpty() }?.let(::File)
    val generatedKotlinSourcesDirFile get() = generatedKotlinSourcesDir.takeIf { it.isNotEmpty() }?.let(::File)
}

class KaptSourceSetModelImpl(
    override val sourceSetName: String,
    override val isTest: Boolean,
    override val generatedSourcesDir: String,
    override val generatedClassesDir: String,
    override val generatedKotlinSourcesDir: String
) : KaptSourceSetModel

interface KaptGradleModel : Serializable {
    val isEnabled: Boolean
    val buildDirectory: File
    val sourceSets: List<KaptSourceSetModel>
}

class KaptGradleModelImpl(
    override val isEnabled: Boolean,
    override val buildDirectory: File,
    override val sourceSets: List<KaptSourceSetModel>
) : KaptGradleModel


class KaptModelBuilderService : AbstractKotlinGradleModelBuilder(), ModelBuilderService.Ex  {
    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder.create(project, e, "Gradle import errors")
            .withDescription("Unable to build kotlin-kapt plugin configuration")
    }

    override fun canBuild(modelName: String?): Boolean = modelName == KaptGradleModel::class.java.name

    override fun buildAll(modelName: String?, project: Project): KaptGradleModelImpl? {
        return buildAll(project, null)
    }

    override fun buildAll(modelName: String, project: Project, builderContext: ModelBuilderContext): KaptGradleModelImpl? {
        return buildAll(project, builderContext)
    }

    private fun buildAll(project: Project, builderContext: ModelBuilderContext?): KaptGradleModelImpl? {
        val androidVariantRequest = AndroidAwareGradleModelProvider.parseParameter(project, builderContext?.parameter)
        if (androidVariantRequest.shouldSkipBuildAllCall()) return null

        val kaptPlugin: Plugin<*>? = project.plugins.findPlugin("kotlin-kapt")
        val kaptIsEnabled = kaptPlugin != null

        val sourceSets = mutableListOf<KaptSourceSetModel>()

        if (kaptIsEnabled) {
            // When running in Android Studio, Android Studio would request specific source sets only to avoid syncing
            // currently not active build variants. We convert names to the lower case to avoid ambiguity with build variants
            // accidentally named starting with upper case.

            val targets = project.getTargets()

            fun handleCompileTask(moduleName: String, compileTask: Task) {
                if (compileTask.javaClass.name !in kotlinCompileJvmTaskClasses) {
                    return
                }

                val sourceSetName = compileTask.getSourceSetName()
                val isTest = sourceSetName.toLowerCase().endsWith("test")

                val kaptGeneratedSourcesDir = getKaptDirectory("getKaptGeneratedSourcesDir", project, sourceSetName)
                val kaptGeneratedClassesDir = getKaptDirectory("getKaptGeneratedClassesDir", project, sourceSetName)
                val kaptGeneratedKotlinSourcesDir = getKaptDirectory("getKaptGeneratedKotlinSourcesDir", project, sourceSetName)
                sourceSets += KaptSourceSetModelImpl(
                    moduleName, isTest, kaptGeneratedSourcesDir, kaptGeneratedClassesDir, kaptGeneratedKotlinSourcesDir
                )
            }

            if (!targets.isNullOrEmpty()) {
                for (target in targets) {
                    if (!isWithJavaEnabled(target)) {
                        continue
                    }

                    val compilations = target.compilations ?: continue
                    for (compilation in compilations) {
                        val compileTask = compilation.getCompileKotlinTaskName(project) ?: continue
                        val moduleName = target.name + compilation.name.capitalize()
                        handleCompileTask(moduleName, compileTask)
                    }
                }
            } else {
                val compileTasks = project.getTarget()?.compilations?.map { compilation -> compilation.getCompileKotlinTaskName(project) }
                    ?: project.getAllTasks(false)[project]
                compileTasks?.forEach{ compileTask ->
                    val sourceSetName = compileTask.getSourceSetName()
                    if (androidVariantRequest.shouldSkipSourceSet(sourceSetName)) return@forEach
                    handleCompileTask(sourceSetName, compileTask)
                }
            }
        }

        return KaptGradleModelImpl(kaptIsEnabled, project.buildDir, sourceSets)
    }

    private fun isWithJavaEnabled(target: Named): Boolean {
        val getWithJavaEnabledMethod = target.javaClass.methods
            .firstOrNull { it.name == "getWithJavaEnabled" && it.parameterCount == 0 } ?: return false

        return getWithJavaEnabledMethod.invoke(target) == true
    }

    private fun getKaptDirectory(funName: String, project: Project, sourceSetName: String): String {
        val kotlinKaptPlugin = project.plugins.findPlugin("kotlin-kapt") ?: return ""

        val targetMethod = kotlinKaptPlugin::class.java.methods.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.name == funName && it.parameterCount == 2
        } ?: return ""

        return (targetMethod(null, project, sourceSetName) as? File)?.absolutePath ?: ""
    }
}