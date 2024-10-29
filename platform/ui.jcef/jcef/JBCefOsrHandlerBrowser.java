// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A wrapper over {@link CefBrowser} that forwards paint requests and notifications to a custom {@link CefRenderHandler}.
 * Key and mouse events are to be sent back to the {@link CefBrowser} via the callbacks: {@link CefBrowser#sendKeyEvent(KeyEvent)},
 * {@link CefBrowser#sendMouseEvent(MouseEvent)}, {@link CefBrowser#sendMouseWheelEvent(MouseWheelEvent)}.
 * <p></p>
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 * <p></p>
 * For the default {@link CefRenderHandler} use {@link JBCefBrowser#create(JBCefBrowserBuilder)} with
 * {@link JBCefBrowserBuilder#setOffScreenRendering(boolean)}.
 *
 * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
 * @see JBCefBrowser#createBuilder
 * @see JBCefBrowser#getCefBrowser
 */
public abstract class JBCefOsrHandlerBrowser extends JBCefBrowserBase {
  /**
   * Creates the browser and immediately creates its native peer.
   * <p></p>
   * In order to use {@link JBCefJSQuery} create the browser via {@link #create(String, CefRenderHandler, boolean)} or
   * {@link #create(String, CefRenderHandler, JBCefClient, boolean)}.
   */
  public static @NotNull JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler) {
    return create(url, renderHandler, true);
  }

  /**
   * Creates the browser and creates its native peer depending on {@code createImmediately}.
   * <p></p>
   * For the browser to start loading call {@link #getCefBrowser()} and {@link CefBrowser#createImmediately()}.
   * <p></p>
   * In order to use {@link JBCefJSQuery} pass {@code createImmediately} as {@code false}, then call {@link CefBrowser#createImmediately()}
   * after all the JS queries are created.
   *
   * @see CefBrowser#createImmediately()
   */
  public static @NotNull JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler, boolean createImmediately) {
    return new JBCefOsrHandlerBrowser(null, url, renderHandler, createImmediately) {
      @Override
      public @Nullable JComponent getComponent() {
        return null;
      }
    };
  }

  /**
   * Creates the browser with the provided {@link JBCefClient} and immediately creates its native peer.
   * <p></p>
   * In order to use {@link JBCefJSQuery} set {@link JBCefClient.Properties#JS_QUERY_POOL_SIZE} before passing the client.
   */
  public static @NotNull JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler, @NotNull JBCefClient client) {
    return create(url, renderHandler, client, true);
  }

  /**
   * Creates the browser and creates its native peer depending on {@code createImmediately}.
   * <p></p>
   * For the browser to start loading call {@link #getCefBrowser()} and {@link CefBrowser#createImmediately()}.
   * <p></p>
   * In order to use {@link JBCefJSQuery} pass {@code createImmediately} as {@code false}, then call {@link CefBrowser#createImmediately()}
   * after all the JS queries are created. Alternatively, pass {@code createImmediately} as {@code true} and set
   * {@link JBCefClient.Properties#JS_QUERY_POOL_SIZE} before passing the client.
   *
   * @see CefBrowser#createImmediately()
   */
  public static @NotNull JBCefOsrHandlerBrowser create(@NotNull String url,
                                              @NotNull CefRenderHandler renderHandler,
                                              @NotNull JBCefClient client,
                                              boolean createImmediately)
  {
    return new JBCefOsrHandlerBrowser(client, url, renderHandler, createImmediately) {
      @Override
      public @Nullable JComponent getComponent() {
        return null;
      }
    };
  }

  /**
   * @param client the client or null to provide default client
   * @param url the url
   * @param renderHandler the render handler
   * @param createImmediately whether the native browser should be created immediately
   */
  protected JBCefOsrHandlerBrowser(@Nullable JBCefClient client,
                                   @Nullable String url,
                                   @NotNull CefRenderHandler renderHandler,
                                   boolean createImmediately)
  {
    super(JBCefBrowser.createBuilder().
            setClient(client).
            setUrl(url).
            setCreateImmediately(createImmediately).
            setOffScreenRendering(true).
            setOSRHandlerFactory(new JBCefOSRHandlerFactory() {
              @Override
              public @NotNull JComponent createComponent(boolean isMouseWheelEventEnabled) {
                return new JComponent() {};
              }
              @Override
              public @NotNull CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
                return renderHandler;
              }
            }));
  }
}
