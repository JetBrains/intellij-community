// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.NonNls
import java.util.*

interface VcsLogUiProperties {
  @RequiresEdt
  operator fun <T> get(property: VcsLogUiProperty<T>): T

  @RequiresEdt
  operator fun <T> set(property: VcsLogUiProperty<T>, value: T)

  fun <T> exists(property: VcsLogUiProperty<T>): Boolean

  @RequiresEdt
  fun addChangeListener(listener: PropertiesChangeListener)

  @RequiresEdt
  fun addChangeListener(listener: PropertiesChangeListener, parent: Disposable)

  @RequiresEdt
  fun removeChangeListener(listener: PropertiesChangeListener)

  open class VcsLogUiProperty<T>(val name: @NonNls String) {
    override fun toString() = name

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val property = other as VcsLogUiProperty<*>
      return name == property.name
    }

    override fun hashCode(): Int = name.hashCode()
  }

  interface PropertiesChangeListener : EventListener {
    fun <T> onPropertyChanged(property: VcsLogUiProperty<T>)
  }
}

internal fun VcsLogUiProperties.onPropertyChange(disposable: Disposable, listener: (VcsLogUiProperties.VcsLogUiProperty<*>) -> Unit) {
  val propertiesChangeListener = object : VcsLogUiProperties.PropertiesChangeListener {
    override fun <T> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) = listener(property)
  }
  addChangeListener(propertiesChangeListener, disposable)
}

internal fun <T> VcsLogUiProperties.getOrNull(property: VcsLogUiProperties.VcsLogUiProperty<T>): T? {
  if (!exists(property)) return null
  return this[property]
}