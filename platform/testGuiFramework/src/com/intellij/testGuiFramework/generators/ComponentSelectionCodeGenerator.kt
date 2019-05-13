// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.generators

import java.awt.Component
import java.awt.Point

interface ComponentSelectionCodeGenerator<ComponentType : Component> : ComponentCodeGenerator<ComponentType> {
  fun generateSelection(cmp: ComponentType, firstPoint: Point, lastPoint: Point): String

  fun generateSelectionCode(cmp: Component, firstPoint: Point, lastPoint: Point): String = generateSelection(typeSafeCast(cmp), firstPoint,
                                                                                                             lastPoint)
}

