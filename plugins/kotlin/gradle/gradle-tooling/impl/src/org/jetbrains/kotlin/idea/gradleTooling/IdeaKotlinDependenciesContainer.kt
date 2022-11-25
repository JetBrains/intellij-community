// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.gradle.idea.proto.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import java.io.Serializable
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


sealed interface IdeaKotlinDependenciesContainer : Serializable {
    operator fun get(sourceSetName: String): Set<IdeaKotlinDependency>
}

private data class IdeaKotlinDeserializedDependenciesContainer(
    private val dependencies: Map<String, Set<IdeaKotlinDependency>>
) : IdeaKotlinDependenciesContainer {
    override fun get(sourceSetName: String): Set<IdeaKotlinDependency> {
        return dependencies[sourceSetName].orEmpty()
    }
}

class IdeaKotlinSerializedDependenciesContainer(
    dependencies: Map<String, List<ByteArray>>
) : IdeaKotlinDependenciesContainer {

    private val readWriteLock = ReentrantReadWriteLock()

    private var serializedDependencies: Map<String, List<ByteArray>>? = dependencies

    private var deserializedDependencies: IdeaKotlinDependenciesContainer? = null

    val isDeserialized: Boolean get() = readWriteLock.read { deserializedDependencies != null }

    override fun get(sourceSetName: String): Set<IdeaKotlinDependency> = readWriteLock.read {
        val deserializedDependencies = this.deserializedDependencies
        if (deserializedDependencies == null) {
            throw NotDeserializedException("${IdeaKotlinDependenciesContainer::class.simpleName} not deserialized yet")
        }

        deserializedDependencies[sourceSetName]
    }

    fun deserialize(context: IdeaKotlinSerializationContext): Unit = readWriteLock.write {
        val serializedDependencies = this.serializedDependencies
        if (serializedDependencies == null) {
            context.logger.warn("${IdeaKotlinDependenciesContainer::class.java.name} already serialized")
            return@write
        }

        val deserialized = serializedDependencies.mapNotNull { (sourceSetName, dependencies) ->
            val deserializedDependencies = dependencies.mapNotNull { dependency ->
                context.IdeaKotlinDependency(dependency)
            }
            sourceSetName to deserializedDependencies.toSet()
        }.toMap()

        this.serializedDependencies = null
        this.deserializedDependencies = IdeaKotlinDeserializedDependenciesContainer(deserialized)
    }

    fun deserializeIfNecessary(context: IdeaKotlinSerializationContext) {
        if (isDeserialized) return
        readWriteLock.write {
            if (isDeserialized) return
            deserialize(context)
        }
    }

    class NotDeserializedException(message: String) : IllegalStateException(message)
}
