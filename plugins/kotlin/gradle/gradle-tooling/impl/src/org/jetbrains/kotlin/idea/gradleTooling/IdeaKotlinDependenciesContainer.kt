// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.idea.proto.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.proto.tcs.toByteArray
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.serialization.ideaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.serialization.ideaKotlinSerializationContextOrNull
import java.io.Serializable


sealed interface IdeaKotlinDependenciesContainer {
    operator fun get(sourceSetName: String): Set<IdeaKotlinDependency>
}

private data class IdeaKotlinDeserializedDependenciesContainer(
    private val dependencies: Map<String, Set<IdeaKotlinDependency>>
) : IdeaKotlinDependenciesContainer, Serializable {
    override fun get(sourceSetName: String): Set<IdeaKotlinDependency> {
        return dependencies[sourceSetName].orEmpty()
    }

    private fun writeReplace(): Any {
        return IdeaKotlinDependenciesContainerSurrogate(dependencies.mapValues { (_, dependencies) ->
            dependencies.map { dependency ->
                dependency.toByteArray(ideaKotlinSerializationContext)
            }
        })
    }
}

class IdeaKotlinSerializedDependenciesContainer(
    private val dependencies: Map<String, List<ByteArray>>
) : IdeaKotlinDependenciesContainer, Serializable {

    override fun get(sourceSetName: String): Set<IdeaKotlinDependency> {
        /* This container is only used to be transported into the IDE */
        throw UnsupportedOperationException("${IdeaKotlinSerializedDependenciesContainer::class.java} can't provide dependencies")
    }

    private fun writeReplace(): Any {
        return IdeaKotlinDependenciesContainerSurrogate(dependencies)
    }
}

private class IdeaKotlinDependenciesContainerSurrogate(private val dependencies: Map<String, List<ByteArray>>) : Serializable {
    private fun readResolve(): Any {
        if (ideaKotlinSerializationContextOrNull == null && GradleVersion.current() != null) {
            return IdeaKotlinSerializedDependenciesContainer(dependencies)
        }
        val deserializedDependencies = dependencies.mapValues { (_, dependencies) ->
            dependencies.mapNotNull { dependency ->
                ideaKotlinSerializationContext.IdeaKotlinDependency(dependency)
            }.toSet()
        }

        return IdeaKotlinDeserializedDependenciesContainer(deserializedDependencies)
    }
}
