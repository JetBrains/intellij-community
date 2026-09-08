// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rd

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RdProtocolDetector {
  fun isRdProtocolDetected(): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<RdProtocolDetector> = ExtensionPointName("com.intellij.rdProtocolDetector")

    /**
     * Checks that there the IDE is currently operating with a plugin/product that required RD protocol (i.e., Rider, CLion Nova, or C++ Plugin in IDEA).
     *
     * This check does NOT include generic Remote Development.
     */
    fun isRdProtocolDetected(): Boolean = EP_NAME.extensionList.any(RdProtocolDetector::isRdProtocolDetected)
  }
}
