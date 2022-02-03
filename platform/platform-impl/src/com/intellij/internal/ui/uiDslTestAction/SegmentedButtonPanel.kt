// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class SegmentedButtonPanel {

  val panel = panel {
    val propertyGraph = PropertyGraph()
    val property = propertyGraph.graphProperty { "" }
    lateinit var segmentedButton: SegmentedButton<String>

    val segmentedButtonRow = row("Segmented Button:") {
      segmentedButton = segmentedButton(generateItems(3), { it })
        .bind(property)
    }

    row("Property value:") {
      val textField = textField()
      property.afterChange {
        textField.component.text = it
      }
      button("Change Property") {
        property.set(textField.component.text)
      }
    }

    row("Options count:") {
      val textField = textField()
        .applyToComponent { text = "6" }
        .component
      button("rebuild") {
        textField.text.toIntOrNull()?.let {
          segmentedButton.items(generateItems(it))
        }
      }
    }

    row {
      checkBox("Enabled")
        .applyToComponent {
          isSelected = true
        }
        .actionListener { _, component -> segmentedButtonRow.enabled(component.isSelected) }
    }

    group("Segmented button without binding") {
      row {
        segmentedButton(generateItems(5), { it })
      }
    }
  }

  private fun generateItems(count: Int): Collection<String> {
    return (1..count).map { "Item $it" }
  }
}
