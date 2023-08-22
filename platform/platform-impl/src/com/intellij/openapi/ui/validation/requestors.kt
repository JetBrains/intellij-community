// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.openapi.observable.util.*
import com.intellij.ui.EditorTextField
import java.awt.ItemSelectable
import javax.swing.text.JTextComponent


val WHEN_TEXT_CHANGED: DialogValidationRequestor.WithParameter<JTextComponent> = DialogValidationRequestor.WithParameter { component ->
  DialogValidationRequestor { parentDisposable, validate ->
    component.whenTextChanged(parentDisposable) { validate() }
  }
}

val WHEN_DOCUMENT_CHANGED: DialogValidationRequestor.WithParameter<EditorTextField> = DialogValidationRequestor.WithParameter { component ->
  DialogValidationRequestor { parentDisposable, validate ->
    component.whenDocumentChanged(parentDisposable) {
      validate()
    }
  }
}

val WHEN_STATE_CHANGED: DialogValidationRequestor.WithParameter<ItemSelectable> = DialogValidationRequestor.WithParameter { component ->
  DialogValidationRequestor { parentDisposable, validate ->
    component.whenStateChanged(parentDisposable) { validate() }
  }
}

val WHEN_PROPERTY_CHANGED: DialogValidationRequestor.WithParameter<ObservableProperty<*>> = DialogValidationRequestor.WithParameter { property ->
  DialogValidationRequestor { parentDisposable, validate ->
    property.whenPropertyChanged(parentDisposable) { validate() }
  }
}

val WHEN_GRAPH_PROPAGATION_FINISHED: DialogValidationRequestor.WithParameter<PropertyGraph> = DialogValidationRequestor.WithParameter { graph ->
  DialogValidationRequestor { parentDisposable, validate ->
    graph.afterPropagation(parentDisposable, validate)
  }
}

@Deprecated("Use WHEN_PROPERTY_CHANGED instead")
val AFTER_PROPERTY_CHANGE: DialogValidationRequestor.WithParameter<ObservableProperty<*>> = WHEN_PROPERTY_CHANGED

@Deprecated("Use WHEN_GRAPH_PROPAGATION_FINISHED instead")
val AFTER_GRAPH_PROPAGATION: DialogValidationRequestor.WithParameter<PropertyGraph> = WHEN_GRAPH_PROPAGATION_FINISHED