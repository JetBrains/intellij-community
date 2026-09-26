// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Experimental
interface EditorHostedComponent {
  /**
   * Whether this component owns the input focus while the focus is inside it. The editor caches the answer, so the
   * value has to stay the same for the life of the component.
   */
  val isInputFocusOwner: Boolean
}