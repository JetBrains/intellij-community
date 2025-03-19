// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.util.Pair;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.browser.CefFrame;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.ui.jcef.JBCefBrowserBase.JBCEFBROWSER_INSTANCE_PROP;

public final class JcefShortcutProvider {
  // Since these CefFrame::* methods are available only with JCEF API 1.1 and higher, we are adding no shortcuts for older JCEF
  private static final List<Pair<String, AnAction>> ourActions =
    isSupportedByJCefApi() ? List.of(createAction("$Cut", CefFrame::cut), createAction("$Copy", CefFrame::copy),
                                     createAction("$Paste", CefFrame::paste), createAction("$SelectAll", CefFrame::selectAll),
                                     createAction("$Undo", CefFrame::undo), createAction("$Redo", CefFrame::redo)) : List.of();

  public static List<Pair<String, AnAction>> getActions() {
    return ourActions;
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

  private static Pair<String, AnAction> createAction(String shortcut, Consumer<? super CefFrame> action) {
    return Pair.create(shortcut, LightEditActionFactory.create(event -> {
      Component component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      if (component == null) return;

      if (component instanceof JComponent && ((JComponent)component).getClientProperty(JBCEFBROWSER_INSTANCE_PROP) != null) {
        Object browser = ((JComponent)component).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
        action.accept(((JBCefBrowserBase)browser).getCefBrowser().getFocusedFrame());
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
      action.accept(((JBCefBrowserBase)browser).getCefBrowser().getFocusedFrame());
    }));
  }

  public static void registerShortcuts(JComponent uiComp, JBCefBrowser jbCefBrowser) {
    ActionManager actionManager = ActionManager.getInstance();
    for (Pair<String, AnAction> action : ourActions) {
      action.second.registerCustomShortcutSet(actionManager.getAction(action.first).getShortcutSet(), uiComp, jbCefBrowser);
    }
  }
}