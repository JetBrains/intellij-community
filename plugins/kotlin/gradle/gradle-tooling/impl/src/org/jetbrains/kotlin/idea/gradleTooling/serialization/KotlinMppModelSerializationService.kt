// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi
import org.jetbrains.plugins.gradle.tooling.serialization.DefaultSerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService
import java.util.ServiceLoader

class KotlinMppModelSerializationService : SerializationService<KotlinMPPGradleModel> {

    private val defaultService = DefaultSerializationService()

    override fun write(`object`: KotlinMPPGradleModel, modelClazz: Class<out KotlinMPPGradleModel>?): ByteArray {
        return defaultService.write(`object`, modelClazz)
    }

    @OptIn(KotlinGradlePluginVersionDependentApi::class)
    override fun read(`object`: ByteArray, modelClazz: Class<out KotlinMPPGradleModel>): KotlinMPPGradleModel {
        val model = defaultService.read(`object`, modelClazz) as KotlinMPPGradleModel

        return KotlinMPPGradleModelImpl(model, mutableMapOf()).copy(
            dependencies = model.dependencies
                ?.let { container -> container as? IdeaKotlinSerializedDependenciesContainer }
                ?.deserialize()
        )
    }

    override fun getModelClass(): Class<out KotlinMPPGradleModel> {
        return KotlinMPPGradleModel::class.java
    }
}

private fun IdeaKotlinSerializedDependenciesContainer.deserialize(): IdeaKotlinDependenciesContainer {
    val serializationContext = ServiceLoader.load(IdeaKotlinSerializationContext::class.java).firstOrNull()
        ?: error("Missing ${IdeaKotlinSerializationContext::class.java.name}")
    return deserialize(serializationContext)
}