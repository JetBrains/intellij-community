// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.cef.browser.CefBrowser;
import org.cef.handler.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author tav
 */
public class JCEFHtmlPanel extends JBCefBrowser {
  private static final JBCefClient ourCefClient = JBCefApp.getInstance().createClient();
  // browser demands some valid URL for loading html content
  private static final String ourUrl = JCEFHtmlPanel.class.getResource(JCEFHtmlPanel.class.getSimpleName() + ".class").toExternalForm();

  private boolean myIsCefBrowserCreated;
  @Nullable private String myHtml;

  private final CefLifeSpanHandler myCefLifeSpanHandler;

  static {
    Disposer.register(ApplicationManager.getApplication(), () -> {
      ourCefClient.getCefClient().dispose();
    });
  }

  public JCEFHtmlPanel() {
    super(ourCefClient);

    ourCefClient.addLifeSpanHandler(myCefLifeSpanHandler = new CefLifeSpanHandlerAdapter() {
      @Override
      public void onAfterCreated(CefBrowser browser) {
        JCEFHtmlPanel panel = (JCEFHtmlPanel)getJBCefBrowser(browser);
        if (panel != null) {
          panel.myIsCefBrowserCreated = true;
          if (panel.myHtml != null) {
            browser.loadString(panel.myHtml, ourUrl);
            panel.myHtml = null;
          }
        }
      }
    }, getCefBrowser());
  }

  public void setHtml(@NotNull String html) {
    final String htmlToRender = prepareHtml(html);
    if (!myIsCefBrowserCreated) {
      myHtml = htmlToRender;
    }
    else {
      getCefBrowser().loadString(htmlToRender, ourUrl);
    }
  }

  @NotNull
  protected String prepareHtml(@NotNull String html) {
    return html;
  }

  @Override
  public void dispose() {
    ourCefClient.removeLifeSpanHandler(myCefLifeSpanHandler, getCefBrowser());
    super.dispose();
  }
}
