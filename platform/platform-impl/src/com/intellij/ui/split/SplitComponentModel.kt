// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.split

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

/**
 * A model object providing data to a frontend component. The component is created by a [SplitComponentProvider] instance with matching id.
 *
 * @see SplitComponentFactory
 */
@ApiStatus.Experimental
interface SplitComponentModel : Disposable {
  val providerId: String
}