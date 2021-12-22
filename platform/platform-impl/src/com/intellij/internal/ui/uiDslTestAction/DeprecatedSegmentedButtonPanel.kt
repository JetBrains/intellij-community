// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class DeprecatedSegmentedButtonPanel {

  val panel = panel {
    val propertyGraph = PropertyGraph()
    val property = propertyGraph.graphProperty { "" }
    lateinit var segmentedButton: SegmentedButton<String>

    row("Segmented Button") {
      segmentedButton = segmentedButton(generateItems(3), property, { s -> s })
    }

    row {
      val label = label("").component
      property.afterChange {
        label.text = "Selection: $it"
      }
    }

    row {
      val textField = textField({ "6" }, {}).component
      button("rebuild") {
        textField.text.toIntOrNull()?.let {
          segmentedButton.rebuild(generateItems(it))
        }
      }
    }
  }

  private fun generateItems(count: Int): Collection<String> {
    return (1..count).map { "Item $it" }
  }
}
