// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Handler of popup trigger mouse events in editor
 *
 * @see EditorEx#installPopupHandler(EditorPopupHandler)
 * @see ContextMenuPopupHandler default implementation
 */
public interface EditorPopupHandler {
  EditorPopupHandler NONE = event -> true;

  /**
   * This method is called when a popup trigger mouse event is received by editor's main area.
   *
   * @return {@code true} if this handler has processed the event, {@code false} if previously installed (or the default one if there are
   * none) handler should be invoked.
   */
  boolean handlePopup(@NotNull EditorMouseEvent event);
}
