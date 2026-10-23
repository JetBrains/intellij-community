// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.gradle.idea.proto.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.proto.tcs.toByteArray
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.serialization.ideaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.serialization.ideaKotlinSerializationContextOrNull
import java.io.Serializable
import java.nio.ByteBuffer


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
        val serializedDependencies = dependencies.mapValues { (_, dependencies) ->
            dependencies.map { dependency ->
                dependency.toByteArray(ideaKotlinSerializationContext)
            }
        }
        return IdeaKotlinDependenciesContainerSurrogate(IdeaKotlinSerializedDependencyPool(serializedDependencies))
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
        return IdeaKotlinDependenciesContainerSurrogate(IdeaKotlinSerializedDependencyPool(dependencies))
    }
}

/**
 * Stores each distinct serialized dependency once to deduplicate passed data via import.
 *
 * Source sets of one project resolve many equal dependencies, for example the Kotlin/Native platform libraries.
 * Without the pool, the model contains the same bytes once for each source set.
 * A source set refers to its dependencies by the index in [dependencies]. The order of the indexes is the classpath order.
 *
 * This class is public only for the tests. Do not use it outside this file.
 */
@ApiStatus.Internal
class IdeaKotlinSerializedDependencyPool private constructor(
    val dependencies: List<ByteArray>,
    val dependencyIndexesBySourceSet: Map<String, IntArray>,
) : Serializable {

    fun toDependenciesBySourceSet(): Map<String, List<ByteArray>> {
        return dependencyIndexesBySourceSet.mapValues { (_, indexes) -> indexes.map { dependencies[it] } }
    }

    companion object {
        operator fun invoke(dependenciesBySourceSet: Map<String, List<ByteArray>>): IdeaKotlinSerializedDependencyPool {
            val dependencies = mutableListOf<ByteArray>()
            val indexByContent = HashMap<ByteBuffer, Int>()
            val indexesBySourceSet = mutableMapOf<String, IntArray>()
            for ((sourceSetName, sourceSetDependencies) in dependenciesBySourceSet) {
                indexesBySourceSet[sourceSetName] = IntArray(sourceSetDependencies.size) { position ->
                    val dependency = sourceSetDependencies[position]
                    indexByContent.getOrPut(ByteBuffer.wrap(dependency)) {
                        dependencies.add(dependency)
                        dependencies.lastIndex
                    }
                }
            }
            return IdeaKotlinSerializedDependencyPool(dependencies, indexesBySourceSet)
        }
    }
}

private class IdeaKotlinDependenciesContainerSurrogate(
    private val pool: IdeaKotlinSerializedDependencyPool
) : Serializable {
    private fun readResolve(): Any {
        if (ideaKotlinSerializationContextOrNull == null && GradleVersion.current() != null) {
            return IdeaKotlinSerializedDependenciesContainer(pool.toDependenciesBySourceSet())
        }

        /* Decode each distinct dependency once, so the source sets share the decoded instances */
        val decodedDependencies = pool.dependencies.map { dependency -> ideaKotlinSerializationContext.IdeaKotlinDependency(dependency) }
        val deserializedDependencies = pool.dependencyIndexesBySourceSet.mapValues { (_, indexes) ->
            indexes.asList().mapNotNull { decodedDependencies[it] }.toSet()
        }

        return IdeaKotlinDeserializedDependenciesContainer(deserializedDependencies)
    }
}
