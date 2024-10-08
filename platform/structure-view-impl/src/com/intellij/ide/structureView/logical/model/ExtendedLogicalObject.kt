// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import org.jetbrains.annotations.ApiStatus

/**
 * Logical elements have not implement this class, but they can if they want to add some specific logic
 */
@ApiStatus.Internal
interface ExtendedLogicalObject {

  /**
   * This equality can break common rules for usual "equal", for example, symmetry rule
   */
  fun logicalEquals(other: Any?): Boolean

}