// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace.Companion.task
import com.intellij.openapi.util.RecursionManager
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class PropertyGraph(debugName: String? = null) {
  private val propagation = AnonymousParallelOperationTrace((if (debugName == null) "" else " of $debugName") + ": Graph propagation")
  private val properties = ConcurrentHashMap<ObservableClearableProperty<*>, PropertyNode>()
  private val dependencies = ConcurrentHashMap<PropertyNode, CopyOnWriteArrayList<Dependency<*>>>()
  private val recursionGuard = RecursionManager.createGuard<PropertyNode>(PropertyGraph::class.java.name)

  fun <T> dependsOn(child: AtomicProperty<T>, parent: ObservableClearableProperty<*>, update: () -> T) {
    val childNode = properties[child] ?: throw IllegalArgumentException("Unregistered child property")
    val parentNode = properties[parent] ?: throw IllegalArgumentException("Unregistered parent property")
    dependencies.putIfAbsent(parentNode, CopyOnWriteArrayList())
    val children = dependencies.getValue(parentNode)
    children.add(Dependency(childNode, child, update))
  }

  fun afterPropagation(listener: () -> Unit) {
    propagation.afterOperation(listener)
  }

  fun register(property: ObservableClearableProperty<*>) {
    val node = PropertyNode()
    properties[property] = node
    property.afterChange {
      recursionGuard.doPreventingRecursion(node, false) {
        propagation.task {
          node.isPropagationBlocked = true
          propagateChange(node)
        }
      }
    }
    property.afterReset {
      node.isPropagationBlocked = false
    }
  }

  private fun propagateChange(parent: PropertyNode) {
    val dependencies = dependencies[parent] ?: return
    for (dependency in dependencies) {
      val child = dependency.node
      if (child.isPropagationBlocked) continue
      recursionGuard.doPreventingRecursion(child, false) {
        dependency.applyUpdate()
        propagateChange(child)
      }
    }
  }

  @TestOnly
  fun isPropagationBlocked(property: ObservableClearableProperty<*>) =
    properties.getValue(property).isPropagationBlocked

  private inner class PropertyNode {
    @Volatile
    var isPropagationBlocked = false
  }

  private class Dependency<T>(val node: PropertyNode, private val property: AtomicProperty<T>, private val update: () -> T) {
    fun applyUpdate() {
      property.updateAndGet { update() }
    }
  }
}