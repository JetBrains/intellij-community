// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util.graph

import org.jetbrains.kotlin.util.graph.DirectedGraph.Edge

/**
 * Tests:
 * [org.jetbrains.kotlin.util.graph.SortTopologicallyTest]
 *
 * @return nodes sorted topologically or `null` if the graph is not acyclic
 */
fun <T> DirectedGraph<T>.sortTopologically(): List<Set<T>>? {
    val graph = MutableDirectedGraphOptimizedForTopologicalSort.fromGraph(this)
    return buildList {
        while (!graph.isEmpty()) {
            add(graph.graphIncomingVertices.takeIf(Set<T>::isNotEmpty) ?: return null)
            graph.removeAllGraphIncomingVertices()
        }
    }
}

private class MutableDirectedGraphOptimizedForTopologicalSort<T> private constructor(
    private val vertices: MutableSet<Vertex<T>>,
    private var _graphIncomingVertices: Set<Vertex<T>>,
) {
    val graphIncomingVertices: Set<T> get() = _graphIncomingVertices.asSequence().map(Vertex<T>::value).toSet()
    fun isEmpty() = vertices.isEmpty()

    fun removeAllGraphIncomingVertices() {
        val newIncomingVertices = _graphIncomingVertices.asSequence().flatMap(Vertex<T>::outcomingVertices)
            .onEach { it.incomingVertices.removeAll(_graphIncomingVertices) }
            .filter { it.incomingVertices.isEmpty() }
            .toSet()
        vertices.removeAll(_graphIncomingVertices)
        _graphIncomingVertices = newIncomingVertices
    }

    private class Vertex<T>(
        val value: T,
        val incomingVertices: MutableSet<Vertex<T>> = mutableSetOf(),
        val outcomingVertices: MutableSet<Vertex<T>> = mutableSetOf(),
    ) {
        override fun equals(other: Any?): Boolean = (other as? Vertex<*>)?.value?.equals(value) == true
        override fun hashCode(): Int = value.hashCode()
    }

    companion object {
        fun <T> fromGraph(graph: DirectedGraph<T>): MutableDirectedGraphOptimizedForTopologicalSort<T> {
            val route = graph.edges.groupBy(Edge<T>::from)
                .mapValues { (_, edges) -> edges.map(Edge<T>::to) }
            val valueToVertex = graph.vertices.associateWith(::Vertex)
            val graphIncomingVertices = graph.vertices - graph.edges.asSequence().map(Edge<T>::to).toSet()
            fun populateIncomingOutcomingNodesDfa(from: Vertex<T>, visited: MutableSet<Vertex<T>> = mutableSetOf()) {
                if (!visited.add(from)) return
                route[from.value]?.asSequence()?.map(valueToVertex::getValue)?.forEach { to ->
                    to.incomingVertices.add(from)
                    from.outcomingVertices.add(to)
                    populateIncomingOutcomingNodesDfa(to, visited)
                }
            }
            graphIncomingVertices.asSequence().map(valueToVertex::getValue).forEach(::populateIncomingOutcomingNodesDfa)
            return MutableDirectedGraphOptimizedForTopologicalSort(
                valueToVertex.values.toMutableSet(),
                graphIncomingVertices.asSequence().map(valueToVertex::getValue).toSet(),
            )
        }
    }
}
