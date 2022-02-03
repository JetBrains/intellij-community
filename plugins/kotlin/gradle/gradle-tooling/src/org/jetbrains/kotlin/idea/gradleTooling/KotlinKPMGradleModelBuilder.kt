// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinModuleBuilder
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinProjectModelSettingsBuilder
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinKpmExtensionReflection
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

class KotlinKPMGradleModelBuilder : AbstractModelBuilderService() {
    override fun canBuild(modelName: String?): Boolean = modelName == KotlinKPMGradleModel::class.java.name

    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder = ErrorMessageBuilder
        .create(project, e, "Gradle import errors")
        .withDescription("Unable to build Kotlin PM20 project configuration")

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): KotlinKPMGradleModel? {
        try {
            val kpmExtension = project.kpmExtension ?: return null
            val kpmExtensionReflection = KotlinKpmExtensionReflection(kpmExtension)
            val kpmProjectSettings = KotlinProjectModelSettingsBuilder.buildComponent(kpmExtensionReflection) ?: return null
            val kotlinNativeHome = KotlinNativeHomeEvaluator.getKotlinNativeHome(project) ?: KotlinMPPGradleModel.NO_KOTLIN_NATIVE_HOME
            val importingContext = KotlinProjectModelImportingContext(project, kpmExtension.javaClass.classLoader)
            val kpmModules = kpmExtensionReflection.modules.orEmpty().mapNotNull { originModule ->
                KotlinModuleBuilder.buildComponent(originModule, importingContext.withFragmentCache())
            }

            return KotlinKPMGradleModelImpl(kpmModules, kpmProjectSettings, kotlinNativeHome)
        } catch (throwable: Throwable) {
            project.logger.error("Failed building KotlinKPMGradleModel", throwable)
            throw throwable
        }
    }

    companion object {
        private const val KPM_GRADLE_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform.pm20"
        private const val KPM_EXTENSION_CLASS_NAME = "org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinPm20ProjectExtension"

        private val Project.kpmPlugin
            get() = project.plugins.findPlugin(KPM_GRADLE_PLUGIN_ID)

        private val Project.kpmExtension
            get() = kpmPlugin?.javaClass?.classLoader?.loadClassOrNull(KPM_EXTENSION_CLASS_NAME)?.let { project.extensions.findByType(it) }
    }
}
