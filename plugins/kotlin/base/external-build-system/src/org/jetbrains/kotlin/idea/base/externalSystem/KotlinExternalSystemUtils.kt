// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinExternalSystemUtils")

package org.jetbrains.kotlin.idea.base.externalSystem

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil

class NodeWithData<T>(val node: DataNode<*>, val data: T)

fun <T : Any> DataNode<*>.findAll(key: Key<T>): List<NodeWithData<T>> {
    val nodes = ExternalSystemApiUtil.findAll(this, key)
    return nodes.mapNotNull { node ->
        val data = node.getData(key) ?: return@mapNotNull null
        NodeWithData(node, data)
    }
}

fun <T : Any> DataNode<*>.find(key: Key<T>): NodeWithData<T>? {
    val node = ExternalSystemApiUtil.find(this, key) ?: return null
    val data = node.getData(key) ?: return null
    return NodeWithData(node, data)
}