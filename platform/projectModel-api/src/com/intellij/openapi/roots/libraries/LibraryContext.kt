// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries

import com.intellij.codeInsight.multiverse.CodeInsightContext
import org.jetbrains.annotations.ApiStatus

// todo IJPL-339 mark experimental
@ApiStatus.Internal
interface LibraryContext : CodeInsightContext {
  fun getLibrary(): Library?
}
