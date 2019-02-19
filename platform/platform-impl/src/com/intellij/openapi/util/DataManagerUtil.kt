// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("DataManagerUtil")

package com.intellij.openapi.util

import com.intellij.ide.DataManager.getDataProvider
import com.intellij.ide.DataManager.registerDataProvider
import com.intellij.openapi.actionSystem.DataKey
import javax.swing.JComponent

fun <T> saveDataByComponent(component: JComponent, dataKey: DataKey<T>, data: T) {
  val dataProvider = getDataProvider(component)
  registerDataProvider(component) {
    when {
      dataKey.`is`(it) -> data
      else -> dataProvider?.getData(it)
    }
  }
}

fun <T> loadDataByComponent(component: JComponent, dataKey: DataKey<T>): T? {
  val dataProvider = getDataProvider(component) ?: return null
  return dataKey.getData(dataProvider)
}
