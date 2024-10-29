// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.structureView.logical.*
import com.intellij.openapi.project.Project

/**
 * Utility class which helps to build full logical model for some element
 */
class LogicalStructureAssembledModel<T> private constructor(
  val project: Project,
  val model: T,
  val parent: LogicalStructureAssembledModel<*>?
) {

  companion object {
    fun <T> getInstance(project: Project, root: T): LogicalStructureAssembledModel<T> {
      return LogicalStructureAssembledModel(project, root, null)
    }
  }

  fun getChildren(): List<LogicalStructureAssembledModel<*>> {
    return LogicalStructureElementsProvider.getProviders(model!!)
      .filter { it !is ConvertElementsProvider }
      .flatMap { it.getElements(model) }
      //.flatMap { ConvertElementsProvider.convert(it) }
      .map { LogicalStructureAssembledModel(project, it, this) }
      .toList()
  }

  /**
   * The grouping element in each pair - Any - can be any object, for which a PresentationProvider is registered
   */
  fun getChildrenGrouped(): List<Pair<Any, () -> List<LogicalStructureAssembledModel<*>>>> {
    val result = mutableListOf<Pair<Any, () -> List<LogicalStructureAssembledModel<*>>>>()
    for (provider in LogicalStructureElementsProvider.getProviders(model!!)) {
      if (provider !is ContainerElementsProvider && provider !is PropertyElementProvider) continue
      if (provider is ContainerGroupedElementsProvider<*, *, *>) {
        for (groupedElement in (provider as ContainerGroupedElementsProvider<T, *, *>).getGroupedElements(model)) {
          result.add(Pair(groupedElement.key!!) { groupedElement.value.map { LogicalStructureAssembledModel(project, it, this) } })
        }
        continue
      }
      val children = {
        provider.getElements(model)
          .map { LogicalStructureAssembledModel(project, it, this) }
      }
      result.add(Pair(provider, children))
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as LogicalStructureAssembledModel<*>
    return this.model == other.model && this.parent == other.parent
  }

  override fun hashCode(): Int {
    return model.hashCode() * (parent?.hashCode() ?: 1)
  }

}