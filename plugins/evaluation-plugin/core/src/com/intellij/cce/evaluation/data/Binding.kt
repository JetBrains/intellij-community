package com.intellij.cce.evaluation.data

import com.intellij.cce.core.Lookup

@Suppress("UNCHECKED_CAST")
class Binding<out B : Bindable<*>> private constructor(val bindable: B, val value: Any) {
  fun dump(lookup: Lookup): Lookup = (bindable as Bindable<Any>).placement.dump(lookup, value)

  companion object {
    fun <T, B : Bindable<T>> create(bindable: B, value: T): Binding<B> = Binding(bindable, value as Any)
  }
}

interface Bindable<T> {
  val placement: DataPlacement<T, *>
}