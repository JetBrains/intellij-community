package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph

internal data class CallEdge(
    val parent: FunctionNode,
    val child: FunctionNode,
    val callSite: String,
    val fileAndLine: String,
) 
    
