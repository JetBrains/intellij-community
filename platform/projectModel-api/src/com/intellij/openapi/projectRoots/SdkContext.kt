// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.codeInsight.multiverse.CodeInsightContext
import org.jetbrains.annotations.ApiStatus

// todo ijpl-339 mark experimental
@ApiStatus.Internal
interface SdkContext : CodeInsightContext {
  fun getSdk(): Sdk?
}