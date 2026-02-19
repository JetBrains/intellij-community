// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * This class allows to disable (temporarily or permanently) showing certain popups on mouse hover (currently, error/warning descriptions
 * and quick documentation on mouse hover are impacted). If several requests to disable popups have been performed, corresponding number of
 * enabling requests must be performed to turn on hover popups again.
 */
@Service
public final class EditorMouseHoverPopupControl {
  private static final Logger LOG = Logger.getInstance(EditorMouseHoverPopupControl.class);
  private static final Key<Integer> MOUSE_TRACKING_DISABLED_COUNT = Key.create("MOUSE_TRACKING_DISABLED_COUNT");

  private final Collection<BiConsumer<@NotNull UserDataHolder, Boolean>> listeners = new CopyOnWriteArrayList<>();

  public static EditorMouseHoverPopupControl getInstance() {
    return ApplicationManager.getApplication().getService(EditorMouseHoverPopupControl.class);
  }

  public static void disablePopups(@NotNull Editor editor) {
    setTrackingDisabled(editor, true);
  }

  public static void enablePopups(@NotNull Editor editor) {
    setTrackingDisabled(editor, false);
  }

  public static void disablePopups(@NotNull Document document) {
    setTrackingDisabled(document, true);
  }

  public static void enablePopups(@NotNull Document document) {
    setTrackingDisabled(document, false);
  }

  public static void disablePopups(@NotNull Project project) {
    setTrackingDisabled(project, true);
  }

  public static void enablePopups(@NotNull Project project) {
    setTrackingDisabled(project, false);
  }

  private static void setTrackingDisabled(@NotNull UserDataHolder holder, boolean value) {
    ThreadingAssertions.assertEventDispatchThread();
    Integer userData = holder.getUserData(MOUSE_TRACKING_DISABLED_COUNT);
    int count = (userData == null ? 0 : userData) + (value ? 1 : -1);
    if (count < 0) {
      LOG.warn(new IllegalStateException("Editor mouse hover popups weren't disabled previously"));
      count = 0;
    }
    holder.putUserData(MOUSE_TRACKING_DISABLED_COUNT, count == 0 ? null : count);
    if ((userData == null) != (count == 0)) {
      EditorMouseHoverPopupControl instance = getInstance();
      if (instance != null) {
        instance.listeners.forEach((listener) -> {
          listener.accept(holder, value);
        });
      }
    }
  }

  public static boolean arePopupsDisabled(@NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    Project project = editor.getProject();
    return editor.getUserData(MOUSE_TRACKING_DISABLED_COUNT) != null ||
           editor.getDocument().getUserData(MOUSE_TRACKING_DISABLED_COUNT) != null ||
           project != null && project.getUserData(MOUSE_TRACKING_DISABLED_COUNT) != null;
  }

  public void addListener(@NotNull Runnable listener) {
    listeners.add((holder, isDisabled) -> {
      listener.run();
    });
  }

  @ApiStatus.Internal
  public void addListener(@NotNull BiConsumer<@NotNull UserDataHolder, Boolean> listener, @NotNull Disposable parentDisposable) {
    listeners.add(listener);
    Disposer.register(parentDisposable, () -> listeners.remove(listener));
  }
}
