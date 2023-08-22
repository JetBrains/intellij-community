// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly

// IDEA-246516
private var cacheInconsistencyAllowed: Boolean = false

@TestOnly
fun allowCacheInconsistency(parent: Disposable) {
  cacheInconsistencyAllowed = true
  Disposer.register(parent, Disposable { cacheInconsistencyAllowed = false })
}

fun mustSkipConsistencyCheck(): Boolean = cacheInconsistencyAllowed