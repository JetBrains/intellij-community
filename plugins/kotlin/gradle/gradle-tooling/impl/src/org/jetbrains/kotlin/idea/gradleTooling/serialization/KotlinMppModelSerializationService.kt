// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi
import org.jetbrains.plugins.gradle.tooling.serialization.DefaultSerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService

/**
 * Right now, this SerializationService is not working in production environments, since this class is not
 * available to the ToolingSerializer.
 */
class KotlinMppModelSerializationService : SerializationService<KotlinMPPGradleModel> {

    private val defaultService = DefaultSerializationService()

    override fun write(`object`: KotlinMPPGradleModel, modelClazz: Class<out KotlinMPPGradleModel>?): ByteArray {
        return defaultService.write(`object`, modelClazz)
    }

    override fun read(`object`: ByteArray, modelClazz: Class<out KotlinMPPGradleModel>): KotlinMPPGradleModel {
        return (defaultService.read(`object`, modelClazz) as KotlinMPPGradleModel).also { model ->
            deserialize(model)
        }
    }

    override fun getModelClass(): Class<out KotlinMPPGradleModel> {
        return KotlinMPPGradleModel::class.java
    }

    companion object {
        @OptIn(KotlinGradlePluginVersionDependentApi::class)
        fun deserialize(
            model: KotlinMPPGradleModel,
            context: IdeaKotlinSerializationContext =
                IdeaKotlinSerializationContext(KotlinMppModelSerializationService::class.java.classLoader)
        ) {
            (model.dependencies as? IdeaKotlinSerializedDependenciesContainer)
                ?.deserializeIfNecessary(IdeaKotlinSerializationContext(KotlinMppModelSerializationService::class.java.classLoader))

            (model.targets + model.sourceSetsByName.values + model.targets.flatMap { it.compilations }).forEach { entity ->
                SerializedExtras.deserializeIfNecessary(entity.extras, context)
            }
        }
    }
}
