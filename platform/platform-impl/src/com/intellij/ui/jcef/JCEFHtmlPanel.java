// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author tav
 */
public class JCEFHtmlPanel extends JBCefBrowser {
  private static final JBCefClient ourCefClient = JBCefApp.getInstance().createClient();

  static {
    Disposer.register(ApplicationManager.getApplication(), ourCefClient);
  }

  private final @NotNull String myUrl;

  public JCEFHtmlPanel(@Nullable String url) {
    this(ourCefClient, url);
  }

  public JCEFHtmlPanel(JBCefClient client, String url) {
    this(false, client, url); // should no pass url to ctor
  }

  public JCEFHtmlPanel(boolean isOffScreenRendering, @Nullable JBCefClient client, @Nullable String url) {
    super(JBCefBrowser.createBuilder().setOffScreenRendering(isOffScreenRendering).setClient(client).setUrl(url));
    myUrl = getCefBrowser().getURL();
  }

  @Override
  protected DefaultCefContextMenuHandler createDefaultContextMenuHandler() {
    return new DefaultCefContextMenuHandler() {
      @Override
      public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        model.clear();
        super.onBeforeContextMenu(browser, frame, params, model);
      }
    };
  }

  public void setHtml(@NotNull String html) {
    String htmlToRender = prepareHtml(html);
    loadHTML(htmlToRender, myUrl);
  }

  protected @NotNull String prepareHtml(@NotNull String html) {
    return html;
  }
}
