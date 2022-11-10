// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProjectContainer
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService
import java.util.*


class IdeaKpmProjectSerializationService : SerializationService<IdeaKpmProjectContainer<*>> {

    // TODO Yaroslav Chernyshev
    //  `test 'runPartialGradleImport' is running in 'lenient' or 'classpath' mode` breaks on KGP 1.6.21 with NoClassDefFoundError
    override fun getModelClass(): Class<out IdeaKpmProjectContainer<*>> = try {
        IdeaKpmProjectContainer::class.java
    } catch (t: NoClassDefFoundError) {
        Nothing::class.java
    }

    override fun write(project: IdeaKpmProjectContainer<*>, modelClazz: Class<out IdeaKpmProjectContainer<*>>): ByteArray {
        return project.binaryOrNull ?: ByteArray(0)
    }

    override fun read(data: ByteArray, modelClazz: Class<out IdeaKpmProjectContainer<*>>): IdeaKpmProjectContainer<*>? {
        if (data.isEmpty()) return null

        val deserializer = ServiceLoader.load(IdeaKpmProjectDeserializer::class.java).firstOrNull()
            ?: error("Missing ${IdeaKpmProjectDeserializer::class.java}")

        return IdeaKpmProjectContainer(deserializer.read(data) ?: return null)
    }
}
