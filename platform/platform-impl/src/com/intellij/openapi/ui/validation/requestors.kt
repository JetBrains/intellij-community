// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.whenStateChanged
import com.intellij.openapi.observable.util.whenTextChanged
import java.awt.ItemSelectable
import javax.swing.text.JTextComponent


val WHEN_TEXT_CHANGED = DialogValidationRequestor.WithParameter<JTextComponent> { textComponent ->
  DialogValidationRequestor { parentDisposable, validate ->
    textComponent.whenTextChanged(parentDisposable) { validate() }
  }
}

val WHEN_STATE_CHANGED = DialogValidationRequestor.WithParameter<ItemSelectable> { component ->
  DialogValidationRequestor { parentDisposable, validate ->
    component.whenStateChanged(parentDisposable) { validate() }
  }
}

val AFTER_GRAPH_PROPAGATION = DialogValidationRequestor.WithParameter<PropertyGraph> { graph ->
  DialogValidationRequestor { parentDisposable, validate ->
    graph.afterPropagation(parentDisposable, validate)
  }
}

val AFTER_PROPERTY_PROPAGATION = DialogValidationRequestor.WithParameter<GraphProperty<*>> { property ->
  DialogValidationRequestor { parentDisposable, validate ->
    property.afterPropagation(parentDisposable, validate)
  }
}

val AFTER_PROPERTY_CHANGE = DialogValidationRequestor.WithParameter<ObservableProperty<*>> { property ->
  DialogValidationRequestor { parentDisposable, validate ->
    property.afterChange(parentDisposable) { validate() }
  }
}