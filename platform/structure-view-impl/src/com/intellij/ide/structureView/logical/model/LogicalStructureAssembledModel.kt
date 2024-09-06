// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.structureView.logical.ContainerElementsProvider
import com.intellij.ide.structureView.logical.ConvertElementsProvider
import com.intellij.ide.structureView.logical.LogicalStructureElementsProvider
import com.intellij.ide.structureView.logical.PropertyElementProvider
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
    return LogicalStructureElementsProvider.getProviders(model!!)
      .mapNotNull { provider ->
        if (provider !is ContainerElementsProvider && provider !is PropertyElementProvider) return@mapNotNull null
        val children = {
          provider.getElements(model)
            .map { LogicalStructureAssembledModel(project, it, this) }
        }
        Pair(provider, children)
      }
      .toList()
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