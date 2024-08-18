// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logical.model

import com.intellij.ide.logical.ContainerElementsProvider
import com.intellij.ide.logical.ConvertElementsProvider
import com.intellij.ide.logical.LogicalStructureElementsProvider
import com.intellij.openapi.project.Project

class LogicalStructureAssembledModel<T>(
  val project: Project,
  val model: T
) {

  fun getChildren(): List<LogicalStructureAssembledModel<*>> {
    return LogicalStructureElementsProvider.getProviders(model!!)
      .flatMap { it.getElements(model) }
      .flatMap { ConvertElementsProvider.convert(it) }
      .map { LogicalStructureAssembledModel(project, it) }
      .toList()
  }

    /**
   * The grouping element in each pair - Any - can be any object, for which a PresentationProvider is registered
   */
  fun getChildrenGrouped(): List<Pair<Any, List<LogicalStructureAssembledModel<*>>>> {
    return LogicalStructureElementsProvider.getProviders(model!!)
      .mapNotNull { provider ->
        if (provider !is ContainerElementsProvider) return@mapNotNull null
        val children = provider.getElements(model)
          .map { LogicalStructureAssembledModel(project, it) }
        Pair(provider, children)
      }
      .toList()
  }

}