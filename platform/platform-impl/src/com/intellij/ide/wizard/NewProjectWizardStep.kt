// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*

abstract class NewProjectWizardStep(
  protected val context: WizardContext,
  protected val propertyGraph: PropertyGraph
) {

  constructor(context: WizardContext) : this(context, PROPERTY_GRAPH_KEY.get(context))

  abstract fun setupUI(builder: RowBuilder)

  abstract fun setupProject(project: Project)

  init {
    PROPERTY_GRAPH_KEY.set(context, propertyGraph)
  }

  companion object {
    val PROPERTY_GRAPH_KEY = Key.create<PropertyGraph>(NewProjectWizardStep::class.java.name + "#" + PropertyGraph::class.java.name)
  }
}