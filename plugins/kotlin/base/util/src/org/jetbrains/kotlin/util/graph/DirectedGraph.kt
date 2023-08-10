// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util.graph

class DirectedGraph<T>(val edges: Set<Edge<T>>) {
    val vertices: Set<T> = edges.asSequence().flatMap { listOf(it.from, it.to) }.toSet()
    override fun toString(): String = "DirectedGraph($edges, $vertices)"
    data class Edge<T>(val from: T, val to: T)
}
