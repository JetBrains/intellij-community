// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

/** `MAX_PATH`: the longest path that a Win32 call takes without a prefix. */
private const val MAX_PATH = 260

/**
 * Makes the Win32 form of [path].
 *
 * A Win32 call takes at most [MAX_PATH] characters. A longer path needs the `\?\` prefix. That prefix turns off
 * the path parsing of Win32, so it needs an absolute path.
 *
 * @return the absolute path, with the `\?\` prefix when the path is long
 */
@ApiStatus.Internal
fun windowsPathString(path: Path): String = windowsPathString(path.toAbsolutePath().pathString)

/**
 * Makes the Win32 form of [absolutePath]. See [windowsPathString].
 *
 * @param absolutePath an absolute path, because the `\?\` prefix does not take a relative one
 */
@ApiStatus.Internal
fun windowsPathString(absolutePath: String): String =
  if (absolutePath.length < MAX_PATH) absolutePath else "\\?\$absolutePath"
