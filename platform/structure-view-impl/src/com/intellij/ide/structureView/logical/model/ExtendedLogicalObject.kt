// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import org.jetbrains.annotations.ApiStatus

/**
 * Logical elements have not implement this class, but they can if they want to add some specific logic
 */
@ApiStatus.Experimental
interface ExtendedLogicalObject {

  /**
   * The method helps prevent recursion in the logical tree.
   * @return true - if elements are not equally themselves but are the same if threat them as parents.
   * This equality can break common rules for usual "equal", for example, symmetry rule
   */
  fun isTheSameParent(other: Any?): Boolean

}