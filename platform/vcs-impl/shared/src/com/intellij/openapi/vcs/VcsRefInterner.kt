// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.util.containers.Interner
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object VcsRefNamesInterner {
  private val interner = Interner.createWeakInterner<String?>()

  @JvmStatic
  operator fun get(name: String): String = interner.intern(name)
}
