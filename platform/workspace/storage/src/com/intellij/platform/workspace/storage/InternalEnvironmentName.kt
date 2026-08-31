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
public sealed interface InternalEnvironmentName {
  public val name: @NonNls String

  /**
   * Returns [name] as a directory name which every operating system accepts.
   * Each character which an operating system forbids becomes `_`.
   */
  public fun asDirName(): @NonNls String = name.replace(FORBIDDEN_DIR_NAME_CHARS, "_").replace(FORBIDDEN_DIR_NAME_TAIL, "_")

  public data object Local : InternalEnvironmentName {
    override val name: String = LOCAL_INTERNAL_ENVIRONMENT_NAME
  }

  public data class Custom(override val name: String) : InternalEnvironmentName

  public companion object {
    private const val LOCAL_INTERNAL_ENVIRONMENT_NAME = "Local"

    @JvmStatic
    public fun of(name: String): InternalEnvironmentName = if (name == LOCAL_INTERNAL_ENVIRONMENT_NAME) Local else Custom(name)
  }
}

// Windows forbids these characters. Every OS forbids the path separator and a control character.
private val FORBIDDEN_DIR_NAME_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

// Windows also drops a dot and a space at the end of a directory name.
private val FORBIDDEN_DIR_NAME_TAIL = Regex("""[. ]+$""")
