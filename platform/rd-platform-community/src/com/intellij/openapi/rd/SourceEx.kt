// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.jetbrains.rd.framework.base.RdReactiveBase
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.ISource

enum class RdEventSource {
  Local,
  Remote,
  Initial
}

/**
 * Advise for protocol entities to get information who initiates events
 */
fun <T> ISource<T>.advise(lifetime: Lifetime, handler: (T, RdEventSource) -> Unit) {
  val rdSource = this as? RdReactiveBase
  if (rdSource == null) throw UnsupportedOperationException("$this should inherit RdReactiveBase in order to use this advice function.")

  var initialChange = true
  advise(lifetime) {
    val eventSource = when {
      initialChange -> RdEventSource.Initial
      rdSource.isLocalChange -> RdEventSource.Local
      else -> RdEventSource.Remote
    }
    handler(it, eventSource)
  }
  initialChange = false
}