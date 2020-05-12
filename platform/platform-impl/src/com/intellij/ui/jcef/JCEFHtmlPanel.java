// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
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

  @NotNull
  private final String myUrl;

  static {
    Disposer.register(ApplicationManager.getApplication(), ourCefClient);
  }

  public JCEFHtmlPanel(@Nullable String url) {
    this(ourCefClient, url);
  }

  public JCEFHtmlPanel(JBCefClient client, String url) {
    super(client, null); // should no pass url to ctor
    myUrl = ObjectUtils.notNull(url, "about:blank");
    if (client != ourCefClient) {
      Disposer.register(this, client);
    }
  }

  @Override
  protected DefaultCefContextMenuHandler createDefaultContextMenuHandler() {
    boolean isInternal = ApplicationManager.getApplication().isInternal();
    return new DefaultCefContextMenuHandler(isInternal) {
      @Override
      public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        model.clear();
        super.onBeforeContextMenu(browser, frame, params, model);
      }
    };

  }

  public void setHtml(@NotNull String html) {
    final String htmlToRender = prepareHtml(html);
    loadHTML(htmlToRender, myUrl);
  }

  @NotNull
  protected String prepareHtml(@NotNull String html) {
    return html;
  }
}
