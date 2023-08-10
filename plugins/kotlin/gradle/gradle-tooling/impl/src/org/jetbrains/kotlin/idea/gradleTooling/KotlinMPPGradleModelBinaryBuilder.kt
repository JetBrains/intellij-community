// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.Exception

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

        val serializedModel = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos -> oos.writeObject(model) }
            baos.toByteArray()
        }

        return KotlinMPPGradleModelBinaryImpl(serializedModel)
    }

    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder
            .create(project, e, "Gradle import errors")
            .withDescription("Unable to build Kotlin project configuration")
    }
}
