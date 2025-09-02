// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import org.jetbrains.annotations.ApiStatus
import java.io.InputStream

@ApiStatus.Internal
interface XIncludeLoader {
  /**
   * @param path absolute path from a resource root, without leading '/' (e.g., `META-INF/extensions.xml`)
   */
  fun loadXIncludeReference(path: String): LoadedXIncludeReference?

  /** @param inputStream lifetime is bound to lifetime of [XIncludeLoader] that produced it  */
  @ApiStatus.Internal
  class LoadedXIncludeReference(val inputStream: InputStream, val diagnosticReferenceLocation: String?)
}