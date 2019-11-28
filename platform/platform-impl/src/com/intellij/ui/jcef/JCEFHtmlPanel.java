// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author tav
 */
public class JCEFHtmlPanel implements Disposable {
  private static final CefClient ourCefClient;
  // browser demands some valid URL for loading html content
  private static final String ourUrl = JCEFHtmlPanel.class.getResource(JCEFHtmlPanel.class.getSimpleName() + ".class").toExternalForm();

  @NotNull private final MyComponent myComponent;
  @NotNull private final CefBrowser myCefBrowser;
  private boolean myIsCefBrowserCreated;
  @Nullable private String myHtml;

  static {
    ourCefClient = JBCefApp.getInstance().createClient();
    ourCefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
      @Override
      public void onAfterCreated(CefBrowser browser) {
        JCEFHtmlPanel panel = getJCEFHtmlPanel(browser);
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
        JCEFHtmlPanel panel = getJCEFHtmlPanel(browser);
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
      ourCefClient.dispose();
    });
  }

  public JCEFHtmlPanel() {
    myComponent = new MyComponent(new BorderLayout());
    myComponent.setBackground(JBColor.background());

    myCefBrowser = ourCefClient.createBrowser("about:blank", false, false);
    myComponent.add(myCefBrowser.getUIComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
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
    myCefBrowser.close(true);
  }

  @Contract("null->null; !null->!null")
  private static JCEFHtmlPanel getJCEFHtmlPanel(CefBrowser browser) {
    if (browser == null) return null;
    return ((MyComponent)browser.getUIComponent().getParent()).getJCEFHtmlPanel();
  }

  private class MyComponent extends JPanel {
    MyComponent(BorderLayout layout) {
      super(layout);
    }

    JCEFHtmlPanel getJCEFHtmlPanel() {
      return JCEFHtmlPanel.this;
    }
  }
}
