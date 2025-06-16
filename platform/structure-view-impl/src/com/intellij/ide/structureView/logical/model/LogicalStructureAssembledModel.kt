// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.structureView.logical.*
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Utility class which helps to build full logical model for some element
 */
@ApiStatus.Experimental
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
    val result = LogicalStructureElementsProvider.getProviders(model!!)
      .flatMap { provider ->
        if (provider is ContainerElementsProvider || provider is PropertyElementProvider) {
          listOf(ProvidedLogicalContainer(provider) { provider.getElements(model) })
        }
        else {
          provider.getElements(model)
        }
      }
      .map { LogicalStructureAssembledModel(project, it, this) }
      .toList()
    if (model is LogicalContainer<*>) {
      return ContainerUtil.concat(
        model.getElements().map { LogicalStructureAssembledModel(project, it, parent) },
        result
      )
    }
    return result
  }

  internal fun hasSameModelParent(): Boolean {
    var parentTmp = parent
    while (parentTmp != null) {
      val first = parentTmp.model
      val second = model
      if (first is ExtendedLogicalObject && first.isTheSameParent(second)
          || second is ExtendedLogicalObject && second.isTheSameParent(first)
          || first == second) return true
      parentTmp = parentTmp.parent
    }
    return false
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