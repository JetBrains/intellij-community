// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly

private var nestedContextAllowed: Int? = null

private fun setNestedContext(value: Int, parent: Disposable) {
  val oldValue = nestedContextAllowed
  if (oldValue == value) return
  nestedContextAllowed = value
  Disposer.register(parent, Disposable {
    require(nestedContextAllowed == 0) {
      "Nested context inference expected $nestedContextAllowed more times"
    }
    nestedContextAllowed = oldValue
  })
}

@TestOnly
fun forbidNestedContext(parent: Disposable) {
  setNestedContext(0, parent)
}

@TestOnly
fun allowNestedContextOnce(parent: Disposable) {
  setNestedContext(1, parent)
}

@TestOnly
fun allowNestedContext(times: Int, parent: Disposable) {
  setNestedContext(times, parent)
}

fun checkNestedContext() {
  val times = nestedContextAllowed ?: return
  if (times > 0) {
    nestedContextAllowed = times - 1
    return
  }
  else {
    error("Unexpected attempt to infer in nested context")
  }
}
