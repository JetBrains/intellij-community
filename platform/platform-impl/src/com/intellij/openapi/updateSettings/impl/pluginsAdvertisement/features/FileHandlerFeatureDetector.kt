// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.features

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

@ApiStatus.Internal
@IntellijInternalApi
interface FileHandlerFeatureDetector {
  val id: String
  val displayName: @Nls Supplier<String>

  fun isSupported(file: VirtualFile): Boolean
}