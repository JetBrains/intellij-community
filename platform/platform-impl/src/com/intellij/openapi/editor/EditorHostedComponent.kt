// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * A component that an editor hosts inside its [content component][Editor.getContentComponent], for example a
 * container of embedded editors or text fields.
 *
 * While the keyboard focus is in such a component or in one of its descendants, and [isInputFocusOwner] is `true`,
 * the input focus belongs to the component and not to the editor. The editor then does not show its caret. The
 * nearest such component above the focus owner decides, so an editor inside the component owns its own focus.
 *
 * The editor reads the Swing hierarchy on each change of the focus owner. Add and remove the component on the EDT.
 *
 * In split mode, editors show only on the frontend, so implement this interface in a frontend module.
 */
@Experimental
interface EditorHostedComponent {
  /**
   * Whether this component owns the input focus while the focus is inside it. The editor caches the answer, so the
   * value has to stay the same for the life of the component.
   */
  val isInputFocusOwner: Boolean
}
