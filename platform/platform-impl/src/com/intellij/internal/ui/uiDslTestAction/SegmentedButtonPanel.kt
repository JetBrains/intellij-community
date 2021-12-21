// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class SegmentedButtonPanel {

  val panel = panel {
    val buttons = listOf("Button 1", "Button 2", "Button Last")
    val propertyGraph = PropertyGraph()
    val property = propertyGraph.graphProperty { "" }
    val rows = mutableMapOf<String, Row>()

    row("Segmented Button") {
      segmentedButton(buttons, property, { s -> s })
    }

    rows[buttons[0]] = row(buttons[0]) {
      textField()
    }
    rows[buttons[1]] = row(buttons[1]) {
      checkBox("checkBox")
    }
    rows[buttons[2]] = row(buttons[2]) {
      button("button") {}
    }

    property.afterChange {
      for ((key, row) in rows) {
        row.visible(key == it)
      }
    }
    property.set(buttons[1])

    val property2 = propertyGraph.graphProperty { "" }
    property2.set(buttons[1])
    row("Disabled Segmented Button") {
      segmentedButton(buttons, property2, { s -> s }).enabled(false)
    }
  }
}