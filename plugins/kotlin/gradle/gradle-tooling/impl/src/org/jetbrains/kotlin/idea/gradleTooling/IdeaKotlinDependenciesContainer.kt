// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.gradle.idea.proto.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import java.io.Serializable


sealed interface IdeaKotlinDependenciesContainer: Serializable {
    operator fun get(sourceSetName: String): Set<IdeaKotlinDependency>
}

private data class IdeaKotlinDeserializedDependenciesContainer(
    private val dependencies: Map<String, Set<IdeaKotlinDependency>>
) : IdeaKotlinDependenciesContainer {
    override fun get(sourceSetName: String): Set<IdeaKotlinDependency> {
        return dependencies[sourceSetName].orEmpty()
    }
}

data class IdeaKotlinSerializedDependenciesContainer(
    private val dependencies: Map<String, List<ByteArray>>
) : IdeaKotlinDependenciesContainer {
    override fun get(sourceSetName: String): Set<IdeaKotlinDependency> {
        throw UnsupportedOperationException("Dependencies are not deserialized yet")
    }

    fun deserialize(context: IdeaKotlinSerializationContext): IdeaKotlinDependenciesContainer {
        val deserialized = dependencies.mapNotNull { (sourceSetName, dependencies) ->
            val deserializedDependencies = dependencies.mapNotNull { dependency ->
                context.IdeaKotlinDependency(dependency)
            }
            sourceSetName to deserializedDependencies.toSet()
        }.toMap()

        return IdeaKotlinDeserializedDependenciesContainer(deserialized)
    }
}
