// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * We could associate the name of environment with `EelDescriptor`.
 * However, we are not sure that we want to expose eel as a dependency of the API module with workspace classes,
 * hence we abstract `EelDescriptor` to a mere string [name].
 */
@ApiStatus.Internal
public interface InternalEnvironmentName {
  public val name: @NonNls String
}