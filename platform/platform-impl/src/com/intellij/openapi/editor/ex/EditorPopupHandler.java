// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Handler of popup trigger mouse events in editor
 *
 * @see EditorEx#setPopupHandler(EditorPopupHandler)
 * @see ContextMenuPopupHandler default implementation
 *
 * @since 2019.1
 */
public interface EditorPopupHandler {
  EditorPopupHandler NONE = event -> {};

  /**
   * This method is called when a popup trigger mouse event is received by editor's main area.
   */
  void handlePopup(@NotNull EditorMouseEvent event);
}
