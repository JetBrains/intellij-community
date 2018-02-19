// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.utils

import com.intellij.testGuiFramework.impl.GuiTestCase
import java.util.*
import kotlin.reflect.KProperty


abstract class TestUtilsClass(val guiTestCase: GuiTestCase)

abstract class TestUtilsClassCompanion<out T : TestUtilsClass>(private val factory: (GuiTestCase) -> T) {
  private val map = WeakHashMap<GuiTestCase, T>()

  operator fun getValue(obj: GuiTestCase, property: KProperty<*>): T {
    return map.getOrPut(obj) {
      factory(obj)
    }
  }
}
