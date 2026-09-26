// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import org.jetbrains.annotations.ApiStatus

/**
 * Allows listening to changes in configuration files.
 *
 * See `com.intellij.configurationStore.StateStorageManagerImpl.addOperationListener`.
 */
@ApiStatus.Internal
interface OperationListener {

  /**
   * Called when a configuration file is written
   *
   * @param fileSpec usually the name of the file
   * @param content the content of the file
   * @param roamingType the roaming type of the file
   */
  fun onWrite(fileSpec: String, content: ByteArray, roamingType: RoamingType)

  /**
   * Called when a configuration file is deleted
   *
   * @param fileSpec usually the name of the file
   * @param roamingType the roaming type of the file
   */
  fun onDelete(fileSpec: String, roamingType: RoamingType)

}
