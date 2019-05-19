// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import java.util.function.Consumer
import com.intellij.util.Consumer as JBConsumer

fun <T> JBConsumer<T>.consumeAll(items: Iterable<T>) {
  items.forEach(this::consume)
}

operator fun <T> Consumer<in T>.plusAssign(elements: Iterable<T>) {
  elements.forEach(this::plusAssign)
}

operator fun <T> Consumer<in T>.plusAssign(elements: Array<out T>) {
  elements.forEach(this::plusAssign)
}

operator fun <T> Consumer<in T>.plusAssign(element: T) {
  accept(element)
}

fun <T> Array<T>.init(): List<T> {
  require(isNotEmpty())
  return dropLast(1)
}

fun <T> recursionAwareLazy(initializer: () -> T): Lazy<T> = RecursionAwareSafePublicationLazy(initializer)
