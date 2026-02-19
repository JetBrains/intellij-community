// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry
import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

interface KotlinMPPGradleModelBinary : Serializable {
    val data: ByteArray
}

private class KotlinMPPGradleModelBinaryImpl(override val data: ByteArray) : KotlinMPPGradleModelBinary

/**
 * Wrapper around [KotlinMPPGradleModelBuilder] which sends the built model in binary form (java.io.Serializable)
 */
class KotlinMPPGradleModelBinaryBuilder : AbstractModelBuilderService() {
    override fun canBuild(modelName: String?): Boolean {
        return modelName == KotlinMPPGradleModelBinary::class.java.name
    }

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
        val model = KotlinMPPGradleModelBuilder().buildAll(
            modelName = KotlinMPPGradleModel::class.java.name, project, context
        ) ?: return null

        return GradleOpenTelemetry.callWithSpan("kotlin_import_daemon_mpp_binary_buildAll") {
            val serializedModel = ByteArrayOutputStream().use { baos ->
                ObjectOutputStream(baos).use { oos -> oos.writeObject(model) }
                baos.toByteArray()
            }

            KotlinMPPGradleModelBinaryImpl(serializedModel)
        }
    }

    override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
        context.messageReporter.createMessage()
            .withGroup(this)
            .withKind(Message.Kind.WARNING)
            .withTitle("Gradle import errors")
            .withText("Unable to build Kotlin project configuration")
            .withException(exception)
            .reportMessage(project)
    }
}
