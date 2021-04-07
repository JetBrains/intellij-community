// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.NSDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class TouchbarSupport {
  private static final Logger LOG = Logger.getInstance(TouchbarSupport.class);

  private static volatile boolean isInitialized;
  private static volatile boolean isEnabled = true;

  public static void initialize() {
    if (isInitialized) {
      return;
    }

    synchronized (TouchbarSupport.class) {
      if (isInitialized) {
        return;
      }

      NST.initialize();

      // calculate isEnabled
      String appId = Helpers.getAppId();
      if (appId == null || appId.isEmpty()) {
        LOG.info("can't obtain application id from NSBundle");
      }
      else if (NSDefaults.isShowFnKeysEnabled(appId)) {
        LOG.info("nst library was loaded, but user enabled fn-keys in touchbar");
        isEnabled = false;
      }

      isInitialized = true;
    }
  }

  public static void onApplicationLoaded() {
    initialize();
    if (!isInitialized || !isEnabled()) {
      return;
    }

    // initialize keyboard listener
    IdeEventQueue.getInstance().addDispatcher(e -> {
      TouchBarsManager.processAWTEvent(e);
      return false;
    }, null);

    // initialize default and tool-window contexts
    CtxDefault.initialize();
    CtxToolWindows.initialize();

    // listen plugins
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        reloadAllActions();
      }
    });

    // add settings item
    CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, IdeBundle.message("settings.menus.group.touch.bar"));
  }

  public static boolean isAvailable() {
    return SystemInfoRt.isMac && NST.isAvailable();
  }

  public static boolean isEnabled() {
    return isAvailable() && isEnabled && Registry.is("ide.mac.touchbar.enabled");
  }

  public static void onUpdateEditorHeader(@NotNull Editor editor) {
    if (!isInitialized || !isEnabled()) {
      return;
    }

    CtxEditors.onUpdateEditorHeader(editor);
  }

  public static void showPopupItems(@NotNull JBPopup popup, @NotNull JComponent popupComponent) {
    if (!isInitialized || !isEnabled()) {
      return;
    }
    final Disposable tb = CtxPopup.showPopupItems(popup, popupComponent);
    if (tb != null)
      Disposer.register(popup, tb);
  }

  public static @Nullable Disposable showDialogButtons(@NotNull Container contentPane) {
    if (!isInitialized || !isEnabled()) {
      return null;
    }

    return CtxDialogs.showDialogButtons(contentPane);
  }

  public static void showDialogButtons(@NotNull Disposable parent, @NotNull Container contentPane) {
    Disposable tb = showDialogButtons(contentPane);
    if (tb != null) {
      Disposer.register(parent, tb);
    }
  }

  public static void reloadAllActions() {
    if (!isInitialized || !isEnabled()) {
      return;
    }

    CtxDefault.reloadAllActions();
    CtxToolWindows.reloadAllActions();
  }
}
