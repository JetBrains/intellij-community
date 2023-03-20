// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl

import java.awt.Component
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.tree.TreePath

abstract class HighlightingTriggerMethods internal constructor() {
  inline fun <reified ComponentType : Component> withSelector(
    noinline selector: ((candidates: Collection<ComponentType>) -> ComponentType?)
  ): HighlightingTriggerMethodsWithType<ComponentType> {
    @Suppress("DEPRECATION")
    return HighlightingTriggerMethodsWithType(ComponentType::class.java, this, selector)
  }

  inline fun <reified ComponentType : Component> component(crossinline finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean) {
    @Suppress("DEPRECATION")
    explicitComponentDetection(ComponentType::class.java, null) { finderFunction(it) }
  }

  inline fun <reified ComponentType : Component> componentPart(crossinline rectangle: TaskRuntimeContext.(ComponentType) -> Rectangle?) {
    @Suppress("DEPRECATION")
    explicitComponentPartDetection(ComponentType::class.java) { rectangle(it) }
  }

  open fun treeItem(checkPath: TaskRuntimeContext.(tree: JTree, path: TreePath) -> Boolean) = Unit

  open fun listItem(checkList: TaskRuntimeContext.(item: Any) -> Boolean) = Unit

  @Deprecated("Use inline version")
  open fun <ComponentType : Component> explicitComponentDetection(
    componentClass: Class<ComponentType>,
    selector: ((candidates: Collection<ComponentType>) -> ComponentType?)?,
    finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean
  )  = Unit

  @Deprecated("Use inline version")
  open fun <ComponentType : Component> explicitComponentPartDetection(
    componentClass: Class<ComponentType>,
    rectangle: TaskRuntimeContext.(ComponentType) -> Rectangle?
  )  = Unit
}

class HighlightingTriggerMethodsWithType<ComponentType : Component> @Deprecated("Do not use directly") constructor(
  val componentClass: Class<ComponentType>,
  val parent: HighlightingTriggerMethods,
  val selector: (candidates: Collection<ComponentType>) -> ComponentType?
) {
  inline fun byComponent(crossinline finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean) {
    @Suppress("DEPRECATION")
    parent.explicitComponentDetection(componentClass, selector) { finderFunction(it) }
  }
}
