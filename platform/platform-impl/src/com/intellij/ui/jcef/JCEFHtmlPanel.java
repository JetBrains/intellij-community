// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tav
 */
public class JCEFHtmlPanel implements Disposable {
  @NotNull
  private final JPanel myPanelWrapper;
  private static final CefApp ourCefApp;
  private static final CefClient ourCefClient;
  private static final Map<CefBrowser, JCEFHtmlPanel> ourCefBrowser2Panel = new HashMap<>();
  // browser demands some valid URL for loading html content
  private final static String ourUrl = JCEFHtmlPanel.class.getResource(JCEFHtmlPanel.class.getSimpleName() + ".class").toExternalForm();

  private @NotNull final CefBrowser myCefBrowser;
  private boolean myIsCefBrowserCreated;
  private @Nullable String myHtml;

  // workaround for the UI garbage issue: keep the browser component min until the browser loads html
  private static final boolean USE_SIZE_WORKAROUND = !SystemInfo.isMac;

  static {
    CefSettings settings = new CefSettings();
    settings.windowless_rendering_enabled = false;
    settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_ERROR;
    if (SystemInfo.isMac) {
      CefApp.startup();
      // todo: move it to jcef
      String JCEF_FRAMEWORKS_PATH = System.getProperty("java.home") + "/Frameworks";
      CefApp.addAppHandler(new CefAppHandlerAdapter(new String[] {
        "--framework-dir-path=" + JCEF_FRAMEWORKS_PATH + "/Chromium Embedded Framework.framework",
        "--browser-subprocess-path=" + JCEF_FRAMEWORKS_PATH + "/jcef Helper.app/Contents/MacOS/jcef Helper"
      }) {});
    }
    else if (SystemInfo.isLinux) {
      CefApp.startup(); // force loading libjcef.so
      String JCEF_PATH = System.getProperty("java.home") + "/lib";
      settings.resources_dir_path = JCEF_PATH;
      settings.locales_dir_path = JCEF_PATH + "/locales";
      settings.browser_subprocess_path = JCEF_PATH + "/jcef_helper";
    }
    ourCefApp = CefApp.getInstance(settings);
    ourCefClient = ourCefApp.createClient();
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
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int i) {
        if (USE_SIZE_WORKAROUND) {
          JCEFHtmlPanel panel = ourCefBrowser2Panel.get(browser);
          if (panel != null && browser.getURL() != null && panel.isHtmlLoaded()) {
            browser.getUIComponent().setSize(panel.myPanelWrapper.getSize());
            panel.myPanelWrapper.revalidate();
          }
        }
      }
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
      ourCefApp.dispose();
    });
  }

  public JCEFHtmlPanel() {
    myPanelWrapper = new JPanel(new BorderLayout());
    myPanelWrapper.setBackground(JBColor.background());

    myCefBrowser = ourCefClient.createBrowser("about:blank", false, false);
    ourCefBrowser2Panel.put(myCefBrowser, this);

    if (USE_SIZE_WORKAROUND) {
      myPanelWrapper.setLayout(null);
      myCefBrowser.getUIComponent().setSize(1, 1);
      myPanelWrapper.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (isHtmlLoaded()) {
            myCefBrowser.getUIComponent().setSize(myPanelWrapper.getSize());
          }
        }
      });
    }
    myPanelWrapper.add(myCefBrowser.getUIComponent(), BorderLayout.CENTER);
  }

  public void setBackground(Color background) {
    myPanelWrapper.setBackground(background);
  }

  private boolean isHtmlLoaded() {
    // return ourUrl.equals(myCefBrowser.getURL()); 99% match due to protocols
    return myCefBrowser.getURL().contains(JCEFHtmlPanel.class.getSimpleName());
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

  public void render() {
  }

  @Nullable
  protected URL getStyle(boolean isDarcula) {
    return null;
  }

  protected void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
  }

  @Override
  public void dispose() {
    ourCefBrowser2Panel.remove(myCefBrowser);
    myCefBrowser.close(true);
  }
}
