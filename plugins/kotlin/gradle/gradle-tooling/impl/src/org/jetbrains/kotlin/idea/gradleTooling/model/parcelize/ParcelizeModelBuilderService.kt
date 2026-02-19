// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling.model.parcelize

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.AbstractKotlinGradleModelBuilder
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.io.Serializable

interface ParcelizeGradleModel : Serializable {
    val isEnabled: Boolean
}

class ParcelizeGradleModelImpl(override val isEnabled: Boolean) : ParcelizeGradleModel

class ParcelizeModelBuilderService : AbstractKotlinGradleModelBuilder() {

    override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
        context.messageReporter.createMessage()
            .withGroup(this)
            .withKind(Message.Kind.WARNING)
            .withTitle("Gradle import errors")
            .withText("Unable to build kotlin-parcelize plugin configuration")
            .withException(exception)
            .reportMessage(project)
    }

    override fun canBuild(modelName: String?): Boolean = modelName == ParcelizeGradleModel::class.java.name

    override fun buildAll(modelName: String?, project: Project): Any {
        val parcelizePlugin: Plugin<*>? = project.plugins.findPlugin("kotlin-parcelize")

        return ParcelizeGradleModelImpl(isEnabled = parcelizePlugin != null)
    }
}