// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util.graph

import org.jetbrains.kotlin.util.graph.DirectedGraph.Edge
import kotlin.test.Test
import kotlin.test.assertEquals

class SortTopologicallyTest {
    @Test
    fun testCyclicWithIncomingVertices() = assertEquals(
        null,
        DirectedGraph(setOf(Edge(1, 2), Edge(2, 1), Edge(3, 1))).sortTopologically()
    )

    @Test
    fun testCyclicWithNoIncomingVertices() = assertEquals(
        null,
        DirectedGraph(setOf(Edge(1, 2), Edge(2, 1))).sortTopologically()
    )

    @Test
    fun testLinear() = assertEquals(
        listOf(setOf(1), setOf(2), setOf(3)),
        DirectedGraph(setOf(Edge(2, 3), Edge(1, 2))).sortTopologically()
    )

    @Test
    fun testGraph() = assertEquals(
        listOf(setOf(1, 2), setOf(3, 4), setOf(5), setOf(6)),
        DirectedGraph(setOf(Edge(1, 3), Edge(3, 5), Edge(2, 5), Edge(2, 4), Edge(4, 6), Edge(5, 6))).sortTopologically()
    )

    @Test
    fun testTree() = assertEquals(
        listOf(setOf(1, 2, 3), setOf(4), setOf(5), setOf(6)),
        DirectedGraph(setOf(Edge(1, 4), Edge(2, 4), Edge(4, 5), Edge(3, 5), Edge(5, 6))).sortTopologically()
    )
}
