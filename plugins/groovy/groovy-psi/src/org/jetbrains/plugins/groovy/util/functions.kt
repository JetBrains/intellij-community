// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.util.Consumer

fun <T> Consumer<T>.consumeAll(items: Iterable<T>) {
  items.forEach(this::consume)
}
