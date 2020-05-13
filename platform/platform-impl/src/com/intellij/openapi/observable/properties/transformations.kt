// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

fun <T> GraphProperty<T>.map(transform: (T) -> T) = transform(transform, { it })
fun <T> GraphProperty<T>.comap(transform: (T) -> T) = transform({ it }, transform)
fun <S, T> GraphProperty<S>.transform(map: (S) -> T, comap: (T) -> S): GraphProperty<T> =
  GraphPropertyView(this, map, comap)

fun <T> ObservableClearableProperty<T>.map(transform: (T) -> T) = transform(transform, { it })
fun <T> ObservableClearableProperty<T>.comap(transform: (T) -> T) = transform({ it }, transform)
fun <S, T> ObservableClearableProperty<S>.transform(map: (S) -> T, comap: (T) -> S): ObservableClearableProperty<T> =
  ObservableClearablePropertyView(this, map, comap)

private class GraphPropertyView<S, T>(
  private val instance: GraphProperty<S>,
  private val map: (S) -> T,
  private val comap: (T) -> S
) : GraphProperty<T>, ObservableClearableProperty<T> by ObservableClearablePropertyView(instance, map, comap) {
  override fun dependsOn(parent: ObservableClearableProperty<*>, default: () -> T) =
    instance.dependsOn(parent) { comap(default()) }

  override fun afterPropagation(listener: () -> Unit) =
    instance.afterPropagation(listener)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return instance == other
  }

  override fun hashCode(): Int {
    return instance.hashCode()
  }
}

private class ObservableClearablePropertyView<S, T>(
  private val instance: ObservableClearableProperty<S>,
  private val map: (S) -> T,
  private val comap: (T) -> S
) : ObservableClearableProperty<T> {
  override fun get(): T =
    map(instance.get())

  override fun set(value: T) =
    instance.set(comap(value))

  override fun reset() =
    instance.reset()

  override fun afterChange(listener: (T) -> Unit) =
    instance.afterChange { listener(map(it)) }

  override fun afterReset(listener: () -> Unit) =
    instance.afterReset(listener)

  override fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) =
    instance.afterChange({ listener(map(it)) }, parentDisposable)

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) =
    instance.afterReset(listener, parentDisposable)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return instance == other
  }

  override fun hashCode(): Int {
    return instance.hashCode()
  }
}