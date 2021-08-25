// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.Key

abstract class NewProjectWizardStepSettings<S : NewProjectWizardStepSettings<S>>(
  key: Key<S>,
  context: WizardContext,
  protected val propertyGraph: PropertyGraph
) {

  constructor(key: Key<S>, context: WizardContext)
    : this(key, context, PROPERTY_GRAPH_KEY.get(context))

  init {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    key.set(context, this as S)
    PROPERTY_GRAPH_KEY.set(context, propertyGraph)
  }

  companion object {
    val PROPERTY_GRAPH_KEY = Key.create<PropertyGraph>(
      NewProjectWizardStepSettings::class.java.name + "#" + PropertyGraph::class.java.name)
  }
}