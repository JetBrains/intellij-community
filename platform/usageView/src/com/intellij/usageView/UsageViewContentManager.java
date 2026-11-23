// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkListener;


public abstract class UsageViewContentManager {
  public static UsageViewContentManager getInstance(Project project) {
    return project.getService(UsageViewContentManager.class);
  }

  public abstract @NotNull Content addContent(@NlsContexts.TabTitle @NotNull String contentName, boolean reusable, @NotNull JComponent component, boolean toOpenInNewTab, boolean isLockable);

  public abstract @NotNull Content addContent(@NlsContexts.TabTitle @NotNull String contentName,
                                              @NlsContexts.TabTitle String tabName,
                                              @NlsContexts.TabTitle String toolwindowTitle,
                                              boolean reusable,
                                              @NotNull JComponent component,
                                              boolean toOpenInNewTab,
                                              boolean isLockable);

  public abstract int getReusableContentsCount();

  public abstract Content getSelectedContent(boolean reusable);

  public abstract Content getSelectedContent();

  @RequiresEdt
  public abstract void closeContent(@NotNull Content usageView);

  /** The Find tool window of the project of this manager. It shows the contents of this manager. */
  @ApiStatus.Experimental
  public abstract @NotNull ToolWindow getToolWindow();

  /** Shows a balloon at {@link #getToolWindow()}. */
  @ApiStatus.Experimental
  @RequiresEdt
  public abstract void notifyByBalloon(@NotNull MessageType type,
                                       @NlsContexts.PopupContent @NotNull String html,
                                       @Nullable Icon icon,
                                       @Nullable HyperlinkListener listener);
}
