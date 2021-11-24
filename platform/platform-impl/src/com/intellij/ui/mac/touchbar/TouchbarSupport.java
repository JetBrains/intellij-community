// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.IdeBundle;
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
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;

public class TouchbarSupport {
  private static final String IS_ENABLED_KEY = "ide.mac.touchbar.enabled";
  private static final Logger LOG = Logger.getInstance(TouchbarSupport.class);
  private static final @NotNull AWTEventListener ourAWTEventListener = e -> {
    TouchBarsManager.processAWTEvent(e);
  };
  private static final long ourEventMask = AWTEvent.FOCUS_EVENT_MASK | AWTEvent.KEY_EVENT_MASK;

  private static volatile boolean isInitialized;
  private static volatile boolean isEnabled = true;

  private static MessageBusConnection ourConnection;

  public static void initialize() {
    if (isInitialized) {
      return;
    }

    synchronized (TouchbarSupport.class) {
      if (isInitialized) {
        return;
      }

      NST.loadLibrary();

      if (!Registry.is(IS_ENABLED_KEY)) {
        LOG.info("touchbar disabled: registry");
        isEnabled = false;
      } else {
        // read isEnabled from OS (i.e. NSDefaults)
        String appId = Helpers.getAppId();
        if (appId == null || appId.isEmpty()) {
          LOG.info("can't obtain application id from NSBundle (touchbar enabled)");
        } else if (NSDefaults.isShowFnKeysEnabled(appId)) {
          // user has enabled setting "FN-keys in touchbar" (global or per-app)
          if (NSDefaults.isFnShowsAppControls()) {
            LOG.info("touchbar enabled: show FN-keys but pressing fn-key toggle to show app-controls");
            isEnabled = true;
          } else {
            LOG.info("touchbar disabled: show fn-keys");
            isEnabled = false;
          }
        } else
          LOG.info("touchbar support is enabled");
      }

      isInitialized = true;
    }
  }

  private static void enableSupport() {
    isEnabled = true;

    // initialize keyboard listener
    Toolkit.getDefaultToolkit().addAWTEventListener(ourAWTEventListener, ourEventMask);

    // initialize default and tool-window contexts
    CtxDefault.initialize();
    CtxToolWindows.initialize();

    // listen plugins
    ourConnection = ApplicationManager.getApplication().getMessageBus().connect();
    ourConnection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        reloadAllActions();
      }
    });

    // add settings item
    CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, IdeBundle.message("settings.menus.group.touch.bar"));
  }

  public static void enable(boolean enable) {
    if (!isInitialized || !isAvailable())
      return;

    if (!enable) {
      if (isEnabled) {
        Toolkit.getDefaultToolkit().removeAWTEventListener(ourAWTEventListener);

        TouchBarsManager.clearAll();
        CtxDefault.disable();
        CtxToolWindows.disable();

        if (ourConnection != null)
          ourConnection.disconnect();
        ourConnection = null;

        CustomActionsSchema.removeSettingsGroup(IdeActions.GROUP_TOUCHBAR);

        isEnabled = false;
      }
      return;
    }

    if (!isEnabled && Registry.is(IS_ENABLED_KEY)) {
      enableSupport();
    }
  }

  public static void onApplicationLoaded() {
    initialize();
    if (!isInitialized || !isEnabled()) {
      return;
    }

    enableSupport();
  }

  public static boolean isAvailable() {
    return SystemInfoRt.isMac && NST.isAvailable();
  }

  public static boolean isEnabled() {
    return isAvailable() && isEnabled && Registry.is(IS_ENABLED_KEY);
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

  public static @Nullable Disposable showWindowActions(@NotNull Component contentPane) {
    if (!isInitialized || !isEnabled()) {
      return null;
    }

    return CtxDialogs.showWindowActions(contentPane);
  }

  public static void showWindowActions(@NotNull Disposable parent, @NotNull Component contentPane) {
    Disposable tb = showWindowActions(contentPane);
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
