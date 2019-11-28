// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tav
 */
public class JCEFHtmlPanel implements Disposable {
  @NotNull
  private final JPanel myPanelWrapper;
  private static final CefClient ourCefClient;
  private static final Map<CefBrowser, JCEFHtmlPanel> ourCefBrowser2Panel = new HashMap<>();
  // browser demands some valid URL for loading html content
  private final static String ourUrl = JCEFHtmlPanel.class.getResource(JCEFHtmlPanel.class.getSimpleName() + ".class").toExternalForm();

  private @NotNull final CefBrowser myCefBrowser;
  private boolean myIsCefBrowserCreated;
  private @Nullable String myHtml;

  static {
    ourCefClient = JBCefApp.getInstance().createClient();
    ourCefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
      @Override
      public void onAfterCreated(CefBrowser browser) {
        JCEFHtmlPanel panel = ourCefBrowser2Panel.get(browser);
        if (panel != null) {
          panel.myIsCefBrowserCreated = true;
          if (panel.myHtml != null) {
            browser.loadString(panel.myHtml, ourUrl);
            panel.myHtml = null;
          }
        }
      }
    });
    ourCefClient.addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        JCEFHtmlPanel panel = ourCefBrowser2Panel.get(browser);
        if (panel != null) {
          panel.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
        }
      }
    });
    ourCefClient.addFocusHandler(new CefFocusHandlerAdapter() {
      @Override
      public boolean onSetFocus(CefBrowser browser, FocusSource source) {
        if (source == FocusSource.FOCUS_SOURCE_NAVIGATION) return true;
        // Workaround: JCEF doesn't change current focus on the client side.
        // Clear the focus manually and this will report focus loss to the client
        // and will let focus return to the client on mouse click.
        // tav [todo]: the opposite is inadequate
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        return false;
      }
    });
    Disposer.register(ApplicationManager.getApplication(), () -> {
      ourCefBrowser2Panel.clear();
      ourCefClient.dispose();
    });
  }

  public JCEFHtmlPanel() {
    myPanelWrapper = new JPanel(new BorderLayout());
    myPanelWrapper.setBackground(JBColor.background());

    myCefBrowser = ourCefClient.createBrowser("about:blank", false, false);
    ourCefBrowser2Panel.put(myCefBrowser, this);

    myPanelWrapper.add(myCefBrowser.getUIComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public JComponent getComponent() {
    return myPanelWrapper;
  }

  public CefBrowser getBrowser() {
    return myCefBrowser;
  }

  public void setHtml(@NotNull String html) {
    final String htmlToRender = prepareHtml(html);
    if (!myIsCefBrowserCreated) {
      myHtml = htmlToRender;
    }
    else {
      myCefBrowser.loadString(htmlToRender, ourUrl);
    }
  }

  @NotNull
  protected String prepareHtml(@NotNull String html) {
    return html;
  }

  protected void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
  }

  @Override
  public void dispose() {
    ourCefBrowser2Panel.remove(myCefBrowser);
    myCefBrowser.close(true);
  }
}
