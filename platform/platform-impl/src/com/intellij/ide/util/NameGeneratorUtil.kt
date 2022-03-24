// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.ide.util.projectWizard.ModuleNameGenerator
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*

fun Cell.installNameGenerators(place: String?, nameProperty: ObservableMutableProperty<String>) {
  for (nameGenerator in ModuleNameGenerator.EP_NAME.extensionList) {
    val nameGeneratorUi = nameGenerator.getUi(place, nameProperty::set)
    if (nameGeneratorUi != null) {
      nameGeneratorUi(pushX)
    }
  }
}

fun Row.installNameGenerators(place: String?, nameProperty: ObservableMutableProperty<String>) {
  for (nameGenerator in ModuleNameGenerator.EP_NAME.extensionList) {
    val nameGeneratorUi = nameGenerator.getUi(place, nameProperty::set)
    if (nameGeneratorUi != null) {
      cell(nameGeneratorUi)
        .horizontalAlign(HorizontalAlign.FILL)
    }
  }
}