// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.util.Pair;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.browser.CefFrame;

import javax.swing.JComponent;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.ui.jcef.JBCefBrowserBase.JBCEFBROWSER_INSTANCE_PROP;

public final class JcefShortcutProvider {
  // Since these CefFrame::* methods are available only with JCEF API 1.1 and higher, we are adding no shortcuts for older JCEF
  private static final List<Pair<String, AnAction>> ourActions =
    isSupportedByJCefApi() ? List.of(
      createAction("$Cut", JcefShortcutProvider::cut),
      createAction("$Copy", JcefShortcutProvider::copy),
      createAction("$Paste", JcefShortcutProvider::paste),
      createAction("$SelectAll", frame(CefFrame::selectAll)),
      createAction("$Undo", frame(CefFrame::undo)),
      createAction("$Redo", frame(CefFrame::redo))) : List.of();

  public static List<Pair<String, AnAction>> getActions() {
    return ourActions;
  }

  // Runs the command in the focused frame of the browser
  private static Consumer<JBCefBrowserBase> frame(Consumer<? super CefFrame> command) {
    return browser -> {
      CefFrame frame = browser.getCefBrowser().getFocusedFrame();
      if (frame != null) command.accept(frame);
    };
  }

  // The IDE process serves the paste when the browser cannot reach the system clipboard, see JBCefLinuxClipboardBridge
  private static void paste(JBCefBrowserBase browser) {
    JBCefClipboardBridge bridge = browser.getClipboardBridge();
    if (bridge != null) {
      bridge.paste();
    }
    else {
      frame(CefFrame::paste).accept(browser);
    }
  }

  private static void copy(JBCefBrowserBase browser) {
    CefFrame frame = browser.getCefBrowser().getFocusedFrame();
    if (frame == null) return;
    JBCefClipboardBridge bridge = browser.getClipboardBridge();
    if (bridge != null) bridge.copy(frame);
    else frame.copy();
  }

  private static void cut(JBCefBrowserBase browser) {
    CefFrame frame = browser.getCefBrowser().getFocusedFrame();
    if (frame == null) return;
    JBCefClipboardBridge bridge = browser.getClipboardBridge();
    if (bridge != null) bridge.cut(frame);
    else frame.cut();
  }

  // This method may be deleted when JCEF API version check is included into JBCefApp#isSupported
  private static boolean isSupportedByJCefApi() {
    try {
      /* getVersionDetails() was introduced alongside JCEF API versioning with first version of 1.1, which also added these necessary
       * for shortcuts to work CefFrame methods. Therefore successful call to getVersionDetails() means our JCEF API is at least 1.1 */
      JCefAppConfig.getVersionDetails();
      return true;
    }
    catch (NoSuchMethodError | JCefVersionDetails.VersionUnavailableException e) {
      Logger.getInstance(JcefShortcutProvider.class).warn("JCEF shortcuts are unavailable (incompatible API)", e);
      return false;
    }
  }

  private static Pair<String, AnAction> createAction(String shortcut, Consumer<? super JBCefBrowserBase> action) {
    return Pair.create(shortcut, LightEditActionFactory.create(event -> {
      Component component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      if (component == null) return;

      if (component instanceof JComponent && ((JComponent)component).getClientProperty(JBCEFBROWSER_INSTANCE_PROP) != null) {
        Object browser = ((JComponent)component).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
        action.accept((JBCefBrowserBase)browser);
        return;
      }

      Component parentComponent = component.getParent();
      if (!(parentComponent instanceof JComponent)) {
        return;
      }
      Object browser = ((JComponent)parentComponent).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
      if (!(browser instanceof JBCefBrowserBase)) {
        return;
      }
      action.accept((JBCefBrowserBase)browser);
    }));
  }

  public static void registerShortcuts(JComponent uiComp, JBCefBrowser jbCefBrowser) {
    for (Pair<String, AnAction> action : ourActions) {
      registerShortcut(action, uiComp, jbCefBrowser);
    }
  }

  /**
   * Registers the clipboard shortcuts needed by {@link JBCefClipboardBridge}.
   */
  static void registerClipboardShortcuts(JComponent uiComp, JBCefBrowser jbCefBrowser) {
    JBCefClipboardBridge bridge = jbCefBrowser.getClipboardBridge();
    for (Pair<String, AnAction> action : ourActions) {
      if (IdeActions.ACTION_PASTE.equals(action.first) ||
          (bridge != null && (IdeActions.ACTION_COPY.equals(action.first) || IdeActions.ACTION_CUT.equals(action.first)))) {
        registerShortcut(action, uiComp, jbCefBrowser);
      }
    }
  }

  private static void registerShortcut(Pair<String, AnAction> action, JComponent uiComp, JBCefBrowser jbCefBrowser) {
    action.second.registerCustomShortcutSet(ActionManager.getInstance().getAction(action.first).getShortcutSet(), uiComp, jbCefBrowser);
  }
}
