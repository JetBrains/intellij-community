// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util

import org.jetbrains.annotations.Nls

interface BundleAdapter {
  @Nls
  fun message(key: String, vararg params: Any?): String
}