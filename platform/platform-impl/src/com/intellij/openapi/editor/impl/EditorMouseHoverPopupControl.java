// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * This class allows to disable (temporarily or permanently) showing certain popups on mouse hover (currently, error/warning descriptions
 * and quick documentation on mouse hover are impacted). If several requests to disable popups have been performed, corresponding number of
 * enabling requests must be performed to turn on hover popups again.
 */
public class EditorMouseHoverPopupControl {
  private static final Logger LOG = Logger.getInstance(EditorMouseHoverPopupControl.class);
  private static final Key<Integer> MOUSE_TRACKING_DISABLED_COUNT = Key.create("MOUSE_TRACKING_DISABLED_COUNT");

  public static void disablePopups(@NotNull Editor editor) {
    setTrackingDisabled(editor, true);
  }

  public static void enablePopups(@NotNull Editor editor) {
    setTrackingDisabled(editor, false);
  }

  private static void setTrackingDisabled(@NotNull Editor editor, boolean value) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Integer userData = editor.getUserData(MOUSE_TRACKING_DISABLED_COUNT);
    int count = (userData == null ? 0 : userData) + (value ? 1 : -1);
    if (count < 0) {
      LOG.warn(new IllegalStateException("Editor mouse hover popups weren't disabled previously"));
      count = 0;
    }
    editor.putUserData(MOUSE_TRACKING_DISABLED_COUNT, count == 0 ? null : count);
  }

  public static boolean arePopupsDisabled(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return editor.getUserData(MOUSE_TRACKING_DISABLED_COUNT) != null ||
           editor.getComponent().getClientProperty(EditorImpl.IGNORE_MOUSE_TRACKING) != null; /* remove this clause in 2020.1 */
  }
}
