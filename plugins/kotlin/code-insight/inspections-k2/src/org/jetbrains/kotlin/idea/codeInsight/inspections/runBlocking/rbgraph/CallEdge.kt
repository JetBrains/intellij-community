package org.jetbrains.kotlin.idea.codeInsight.inspections.runBlocking.rbgraph

internal data class CallEdge(
    val parent: FunctionNode,
    val child: FunctionNode,
    val callSite: String,
    val fileAndLine: String,
) 
    
