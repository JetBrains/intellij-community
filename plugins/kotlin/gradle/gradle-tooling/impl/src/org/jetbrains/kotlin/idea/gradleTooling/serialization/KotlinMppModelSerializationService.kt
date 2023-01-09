// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

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

    @OptIn(KotlinGradlePluginVersionDependentApi::class)
    override fun read(`object`: ByteArray, modelClazz: Class<out KotlinMPPGradleModel>): KotlinMPPGradleModel {
        val model = defaultService.read(`object`, modelClazz) as KotlinMPPGradleModel
        (model.dependencies as? IdeaKotlinSerializedDependenciesContainer)
            ?.deserialize(IdeaKotlinSerializationContext(KotlinMppModelSerializationService::class.java.classLoader))
        return model
    }

    override fun getModelClass(): Class<out KotlinMPPGradleModel> {
        return KotlinMPPGradleModel::class.java
    }
}
