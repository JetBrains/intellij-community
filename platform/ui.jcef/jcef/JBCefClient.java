// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.jcef.JBCefJSQuery.JSQueryFunc;
import com.intellij.util.ObjectUtils;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.cef.security.CefSSLInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper over {@link CefClient}.
 * <p>
 * Provides facilities to add multiple handlers of the same type ({@code CefClient} doesn't).
 * All the handlers of the same type are called in the "last-added-last-called" order.
 * <p>
 * There are two ways to handle returning values.
 * 1. Call all handlers and return true as the aggregate result if any handler returns true.
 * 2. Call handler until the first handler returns true or not null value.
 * Check the implementation of the corresponding handler in this class if using multiple handlers is needed.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/jcef.html">Embedded Browser (JCEF) (IntelliJ Platform Docs)</a>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"}) // [tav] todo: remove it ( or add*Handler methods not yet used)
public final class JBCefClient implements JBCefDisposable {
  private static final Logger LOG = Logger.getInstance(JBCefClient.class);

  /**
   * @see #setProperty(String, Object)
   */
  public static final class Properties {
    /**
     * Defines the size of the pool used by {@link JBCefJSQuery} after a native browser has been created.
     * <p>
     * Accepts {@link Integer} values. JCEF does not allow registering new JavaScript queries after a native browser
     * has been created. To work around this limitation, a pool of JS query slots can be reserved ahead. One slot
     * corresponds to a single {@link JBCefJSQuery} instance. The pool is not created by default unless it is explicitly
     * requested via this property. The property should be added to a client before the first browser associated
     * with the client is added to a UI hierarchy, otherwise it will have no effect.
     * <p>
     * When a {@link JBCefJSQuery} is disposed, its JS query function ({@link JBCefJSQuery#getFuncName}) is returned
     * to the pool as a free slot and is then reused by a newly created {@link JBCefJSQuery}.
     */
    public static final @NotNull String JS_QUERY_POOL_SIZE = "JBCefClient.JSQuery.poolSize";

    static {
      PropertiesHelper.setType(JS_QUERY_POOL_SIZE, Integer.class);
    }
  }

  private final @NotNull PropertiesHelper myPropertiesHelper = new PropertiesHelper();

  private static final int JS_QUERY_POOL_DEFAULT_SIZE = RegistryManager.getInstance().intValue("ide.browser.jcef.jsQueryPoolSize");
  private static final int JS_QUERY_POOL_MAX_SIZE = 10000;

  private final @NotNull CefClient myCefClient;
  private final @NotNull DisposeHelper myDisposeHelper = new DisposeHelper();
  private volatile @Nullable JSQueryPool myJSQueryPool;
  private final @NotNull AtomicInteger myJSQueryCounter = new AtomicInteger(0);

  private final HandlerSupport<CefContextMenuHandler> myContextMenuHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDialogHandler> myDialogHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDisplayHandler> myDisplayHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDownloadHandler> myDownloadHandler = new HandlerSupport<>();
  private final HandlerSupport<CefDragHandler> myDragHandler = new HandlerSupport<>();
  private final HandlerSupport<CefPermissionHandler> myPermissionHandler = new HandlerSupport<>();
  private final HandlerSupport<CefFocusHandler> myFocusHandler = new HandlerSupport<>();
  private final HandlerSupport<CefJSDialogHandler> myJSDialogHandler = new HandlerSupport<>();
  private final HandlerSupport<CefKeyboardHandler> myKeyboardHandler = new HandlerSupport<>();
  private final HandlerSupport<CefLifeSpanHandler> myLifeSpanHandler = new HandlerSupport<>();
  private final HandlerSupport<CefLoadHandler> myLoadHandler = new HandlerSupport<>();
  private final HandlerSupport<CefRequestHandler> myRequestHandler = new HandlerSupport<>();

  JBCefClient(@NotNull CefClient client) {
    myCefClient = client;
    Disposer.register(JBCefApp.getInstance().getDisposable(), this);

    Runnable createPool = () -> {
      if (myJSQueryPool != null) {
        LOG.warn("JSQueryPool has already been created, this request will be ignored");
        return;
      }
      myJSQueryPool = JSQueryPool.create(this);
    };
    addPropertyChangeListener(Properties.JS_QUERY_POOL_SIZE, evt -> {
      if (evt.getNewValue() != null) {
        createPool.run(); // no need to sync it as the property change firing is sync'ed
      }
    });
    if (JS_QUERY_POOL_DEFAULT_SIZE > 0) {
      createPool.run();
    }
  }

  public @NotNull CefClient getCefClient() {
    return myCefClient;
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      try {
        myCefClient.dispose();
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    });
  }

  @Override
  public boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  /**
   * Supports {@link Properties}.
   *
   * @throws IllegalArgumentException if the value has wrong type or format
   */
  public void setProperty(@NotNull String name, @Nullable Object value) {
    myPropertiesHelper.setProperty(name, value);
  }

  /**
   * @see #setProperty(String, Object)
   */
  public @Nullable Object getProperty(@NotNull String name) {
    return myPropertiesHelper.getProperty(name);
  }

  /**
   * @see #setProperty(String, Object)
   */
  @SuppressWarnings("SameParameterValue")
  void addPropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertiesHelper.addPropertyChangeListener(name, listener);
  }

  /**
   * @see #setProperty(String, Object)
   */
  void removePropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertiesHelper.removePropertyChangeListener(name, listener);
  }

  @Nullable
  JSQueryPool getJSQueryPool() {
    return myJSQueryPool;
  }

  int nextJSQueryIndex() {
    return myJSQueryCounter.incrementAndGet();
  }

  static class JSQueryPool {
    private final List<JSQueryFunc> myPool;
    private final int mySizeLimit;

    static @Nullable JSQueryPool create(@NotNull JBCefClient client) {
      int poolSize = client.myPropertiesHelper.intValue(Properties.JS_QUERY_POOL_SIZE, JS_QUERY_POOL_DEFAULT_SIZE);
      if (poolSize > 0) {
        poolSize = Math.min(poolSize, JS_QUERY_POOL_MAX_SIZE);
        return new JSQueryPool(client, poolSize);
      }
      return null;
    }

    JSQueryPool(@NotNull JBCefClient client, int poolSize) {
      mySizeLimit = poolSize;
      myPool = Collections.synchronizedList(new LinkedList<>());
      // populate all the slots ahead
      for (int i = 0; i < poolSize; i++) {
        myPool.add(i, new JSQueryFunc(client, i, true));
      }
    }

    public @Nullable JSQueryFunc useFreeSlot() {
      if (myPool.isEmpty()) {
        LOG.warn("JavaScript query pool is over [size: " + mySizeLimit + "]", new Throwable());
        return null;
      }
      return myPool.remove(0);
    }

    public void releaseUsedSlot(@NotNull JSQueryFunc func) {
      myPool.add(func);
    }
  }

  public JBCefClient addContextMenuHandler(@NotNull CefContextMenuHandler handler, @NotNull CefBrowser browser) {
    return myContextMenuHandler.add(handler, browser, () -> {
      myCefClient.addContextMenuHandler(new CefContextMenuHandler() {
        @Override
        public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
          myContextMenuHandler.handleAll(browser, handler -> {
            handler.onBeforeContextMenu(browser, frame, params, model);
          });
        }

        @Override
        public boolean runContextMenu(CefBrowser browser,
                                      CefFrame frame,
                                      CefContextMenuParams params,
                                      CefMenuModel model,
                                      CefRunContextMenuCallback callback) {
          return myContextMenuHandler.handleBooleanFirst(browser, handler -> {
            return handler.runContextMenu(browser, frame, params, model, callback);
          });
        }

        @Override
        public boolean onContextMenuCommand(CefBrowser browser,
                                            CefFrame frame,
                                            CefContextMenuParams params,
                                            int commandId,
                                            int eventFlags) {
          return myContextMenuHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onContextMenuCommand(browser, frame, params, commandId, eventFlags);
          });
        }

        @Override
        public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
          myContextMenuHandler.handleAll(browser, handler -> {
            handler.onContextMenuDismissed(browser, frame);
          });
        }
      });
    });
  }

  public void removeContextMenuHandler(@NotNull CefContextMenuHandler handler, @NotNull CefBrowser browser) {
    myContextMenuHandler.remove(handler, browser, () -> myCefClient.removeContextMenuHandler());
  }

  public JBCefClient addDialogHandler(@NotNull CefDialogHandler handler, @NotNull CefBrowser browser) {
    return myDialogHandler.add(handler, browser, () -> {
      myCefClient.addDialogHandler(new CefDialogHandler() {
        @Override
        public boolean onFileDialog(CefBrowser browser,
                                    FileDialogMode mode,
                                    String title,
                                    String defaultFilePath,
                                    @SuppressWarnings("UseOfObsoleteCollectionType") Vector<String> acceptFilters,
                                    @SuppressWarnings("UseOfObsoleteCollectionType") Vector<String> acceptExtensions,
                                    @SuppressWarnings("UseOfObsoleteCollectionType") Vector<String> acceptDescriptions,
                                    CefFileDialogCallback callback) {
          return myDialogHandler.handleBooleanFirst(browser, handler -> {
            return handler.onFileDialog(browser, mode, title, defaultFilePath, acceptFilters, acceptExtensions, acceptDescriptions, callback);
          });
        }
      });
    });
  }

  public void removeDialogHandler(@NotNull CefDialogHandler handler, @NotNull CefBrowser browser) {
    myDialogHandler.remove(handler, browser, () -> myCefClient.removeDialogHandler());
  }

  public JBCefClient addDisplayHandler(@NotNull CefDisplayHandler handler, @NotNull CefBrowser browser) {
    return myDisplayHandler.add(handler, browser, () -> {
      myCefClient.addDisplayHandler(new CefDisplayHandler() {
        @Override
        public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
          myDisplayHandler.handleAll(browser, handler -> {
            handler.onAddressChange(browser, frame, url);
          });
        }

        @Override
        public void onTitleChange(CefBrowser browser, String title) {
          myDisplayHandler.handleAll(browser, handler -> {
            handler.onTitleChange(browser, title);
          });
        }

        @Override
        public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
          // Implement if needed
        }

        @Override
        public boolean onTooltip(CefBrowser browser, String text) {
          return myDisplayHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onTooltip(browser, text);
          });
        }

        @Override
        public void onStatusMessage(CefBrowser browser, String value) {
          myDisplayHandler.handleAll(browser, handler -> {
            handler.onStatusMessage(browser, value);
          });
        }

        @Override
        public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
          return myDisplayHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onConsoleMessage(browser, level, message, source, line);
          });
        }

        @Override
        public boolean onCursorChange(CefBrowser browser, int cursorType) {
          return myDisplayHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onCursorChange(browser, cursorType);
          });
        }
      });
    });
  }

  public void removeDisplayHandler(@NotNull CefDisplayHandler handler, @NotNull CefBrowser browser) {
    myDisplayHandler.remove(handler, browser, () -> myCefClient.removeDisplayHandler());
  }

  public JBCefClient addDownloadHandler(@NotNull CefDownloadHandler handler, @NotNull CefBrowser browser) {
    return myDownloadHandler.add(handler, browser, () -> {
      myCefClient.addDownloadHandler(new CefDownloadHandler() {
        @Override
        public boolean onBeforeDownload(CefBrowser browser,
                                     CefDownloadItem downloadItem,
                                     String suggestedName,
                                     CefBeforeDownloadCallback callback) {
          return myDownloadHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onBeforeDownload(browser, downloadItem, suggestedName, callback);
          });
        }

        @Override
        public void onDownloadUpdated(CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
          myDownloadHandler.handleAll(browser, handler -> {
            handler.onDownloadUpdated(browser, downloadItem, callback);
          });
        }
      });
    });
  }

  public void removeDownloadHandle(@NotNull CefDownloadHandler handler, @NotNull CefBrowser browser) {
    myDownloadHandler.remove(handler, browser, () -> myCefClient.removeDownloadHandler());
  }

  public JBCefClient addDragHandler(@NotNull CefDragHandler handler, @NotNull CefBrowser browser) {
    return myDragHandler.add(handler, browser, () -> {
      myCefClient.addDragHandler(new CefDragHandler() {
        @Override
        public boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) {
          return myDragHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onDragEnter(browser, dragData, mask);
          });
        }
      });
    });
  }

  public void removeDragHandler(@NotNull CefDragHandler handler, @NotNull CefBrowser browser) {
    myDragHandler.remove(handler, browser, () -> myCefClient.removeDragHandler());
  }

  public JBCefClient addPermissionHandler(@NotNull CefPermissionHandler handler, @NotNull CefBrowser browser) {
    return myPermissionHandler.add(handler, browser, () -> {
      myCefClient.addPermissionHandler(new CefPermissionHandler() {

        @Override
        public boolean onRequestMediaAccessPermission(CefBrowser browser,
                                                      CefFrame frame,
                                                      String requesting_url,
                                                      int requested_permissions,
                                                      CefMediaAccessCallback callback) {
          Boolean res = myPermissionHandler.handleBooleanFirst(browser, handler -> {
            return handler.onRequestMediaAccessPermission(browser, frame, requesting_url, requested_permissions, callback);
          });
          return ObjectUtils.notNull(res, false);
        }
      });
    });
  }

  public JBCefClient addFocusHandler(@NotNull CefFocusHandler handler, @NotNull CefBrowser browser) {
    return myFocusHandler.add(handler, browser, () -> {
      myCefClient.addFocusHandler(new CefFocusHandler() {
        @Override
        public void onTakeFocus(CefBrowser browser, boolean next) {
          myFocusHandler.handleAll(browser, handler -> {
            handler.onTakeFocus(browser, next);
          });
        }

        @Override
        public boolean onSetFocus(CefBrowser browser, FocusSource source) {
          return myFocusHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onSetFocus(browser, source);
          });
        }

        @Override
        public void onGotFocus(CefBrowser browser) {
          myFocusHandler.handleAll(browser, handler -> {
            handler.onGotFocus(browser);
          });
        }
      });
    });
  }

  public void removeFocusHandler(@NotNull CefFocusHandler handler, @NotNull CefBrowser browser) {
    myFocusHandler.remove(handler, browser, () -> myCefClient.removeFocusHandler());
  }

  public JBCefClient addJSDialogHandler(@NotNull CefJSDialogHandler handler, @NotNull CefBrowser browser) {
    return myJSDialogHandler.add(handler, browser, () -> {
      myCefClient.addJSDialogHandler(new CefJSDialogHandler() {
        @Override
        public boolean onJSDialog(CefBrowser browser,
                                  String origin_url,
                                  JSDialogType dialog_type,
                                  String message_text,
                                  String default_prompt_text,
                                  CefJSDialogCallback callback,
                                  BoolRef suppress_message) {
          return myJSDialogHandler.handleBooleanFirst(browser, handler -> {
            return handler.onJSDialog(browser, origin_url, dialog_type, message_text, default_prompt_text, callback, suppress_message);
          });
        }

        @Override
        public boolean onBeforeUnloadDialog(CefBrowser browser, String message_text, boolean is_reload, CefJSDialogCallback callback) {
          return myJSDialogHandler.handleBooleanFirst(browser, handler -> {
            return handler.onBeforeUnloadDialog(browser, message_text, is_reload, callback);
          });
        }

        @Override
        public void onResetDialogState(CefBrowser browser) {
          myJSDialogHandler.handleAll(browser, handler -> {
            handler.onResetDialogState(browser);
          });
        }

        @Override
        public void onDialogClosed(CefBrowser browser) {
          myJSDialogHandler.handleAll(browser, handler -> {
            handler.onDialogClosed(browser);
          });
        }
      });
    });
  }

  public void removeJSDialogHandler(@NotNull CefJSDialogHandler handler, @NotNull CefBrowser browser) {
    myJSDialogHandler.remove(handler, browser, () -> myCefClient.removeJSDialogHandler());
  }

  public JBCefClient addKeyboardHandler(@NotNull CefKeyboardHandler handler, @NotNull CefBrowser browser) {
    return myKeyboardHandler.add(handler, browser, () -> {
      myCefClient.addKeyboardHandler(new CefKeyboardHandler() {
        @Override
        public boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
          return myKeyboardHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onPreKeyEvent(browser, event, is_keyboard_shortcut);
          });
        }

        @Override
        public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
          return myKeyboardHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onKeyEvent(browser, event);
          });
        }
      });
    });
  }

  public void removeKeyboardHandler(@NotNull CefKeyboardHandler handler, @NotNull CefBrowser browser) {
    myKeyboardHandler.remove(handler, browser, () -> myCefClient.removeKeyboardHandler());
  }

  public JBCefClient addLifeSpanHandler(@NotNull CefLifeSpanHandler handler, @NotNull CefBrowser browser) {
    return myLifeSpanHandler.add(handler, browser, () -> {
      myCefClient.addLifeSpanHandler(new CefLifeSpanHandler() {
        @Override
        public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
          return myLifeSpanHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onBeforePopup(browser, frame, target_url, target_frame_name);
          });
        }

        @Override
        public void onAfterCreated(CefBrowser browser) {
          myLifeSpanHandler.handleAll(browser, handler -> {
            handler.onAfterCreated(browser);
          });
        }

        @Override
        public void onAfterParentChanged(CefBrowser browser) {
          myLifeSpanHandler.handleAll(browser, handler -> {
            handler.onAfterParentChanged(browser);
          });
        }

        @Override
        public boolean doClose(CefBrowser browser) {
          return myLifeSpanHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.doClose(browser);
          });
        }

        @Override
        public void onBeforeClose(CefBrowser browser) {
          myLifeSpanHandler.handleAll(browser, handler -> {
            handler.onBeforeClose(browser);
          });
        }
      });
    });
  }

  public void removeLifeSpanHandler(@NotNull CefLifeSpanHandler handler, @NotNull CefBrowser browser) {
    myLifeSpanHandler.remove(handler, browser, () -> myCefClient.removeLifeSpanHandler());
  }

  public JBCefClient addLoadHandler(@NotNull CefLoadHandler handler, @NotNull CefBrowser browser) {
    return myLoadHandler.add(handler, browser, () -> {
      myCefClient.addLoadHandler(new CefLoadHandler() {
        @Override
        public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
          myLoadHandler.handleAll(browser, handler -> {
            handler.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
          });
        }

        @Override
        public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
          myLoadHandler.handleAll(browser, handler -> {
            handler.onLoadStart(browser, frame, transitionType);
          });
        }

        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
          myLoadHandler.handleAll(browser, handler -> {
            handler.onLoadEnd(browser, frame, httpStatusCode);
          });
        }

        @Override
        public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
          myLoadHandler.handleAll(browser, handler -> {
            handler.onLoadError(browser, frame, errorCode, errorText, failedUrl);
          });
        }
      });
    });
  }

  public void removeLoadHandler(@NotNull CefLoadHandler handler, @NotNull CefBrowser browser) {
    myLoadHandler.remove(handler, browser, () -> myCefClient.removeLoadHandler());
  }

  public JBCefClient addRequestHandler(@NotNull CefRequestHandler handler, @NotNull CefBrowser browser) {
    return myRequestHandler.add(handler, browser, () -> {
      myCefClient.addRequestHandler(new CefRequestHandler() {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
          return myRequestHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onBeforeBrowse(browser, frame, request, user_gesture, is_redirect);
          });
        }

        @Override
        public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
          return myRequestHandler.handleBooleanReturnAnyOf(browser, handler -> {
            return handler.onOpenURLFromTab(browser, frame, target_url, user_gesture);
          });
        }

        @Override
        public @Nullable CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser,
                                                                             CefFrame frame,
                                                                             CefRequest request,
                                                                             boolean isNavigation,
                                                                             boolean isDownload,
                                                                             String requestInitiator,
                                                                             BoolRef disableDefaultHandling)
        {
          return myRequestHandler.handleFirst(browser, handler -> {
            return handler.getResourceRequestHandler(browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling);
          });
        }

        @Override
        public boolean getAuthCredentials(CefBrowser browser,
                                          String origin_url,
                                          boolean isProxy,
                                          String host,
                                          int port,
                                          String realm,
                                          String scheme,
                                          CefAuthCallback callback)
        {
          return myRequestHandler.handleBooleanFirst(browser, handler -> {
            return handler.getAuthCredentials(browser, origin_url, isProxy, host, port, realm, scheme, callback);
          });
        }

        @Override
        public boolean onCertificateError(CefBrowser browser,
                                          CefLoadHandler.ErrorCode cert_error,
                                          String request_url,
                                          CefSSLInfo sslInfo,
                                          CefCallback callback) {
          List<CefRequestHandler> handlers = myRequestHandler.get(browser);
          if (handlers == null) {
            return false;
          }

          boolean result = false;
          for (CefRequestHandler handler: handlers) {
            result |= handler.onCertificateError(browser, cert_error, request_url, sslInfo, callback);
          }

          return result;
        }

        @Override
        public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status, int error_code, String error_string) {
          myRequestHandler.handleAll(browser, handler -> {
            handler.onRenderProcessTerminated(browser, status, error_code, error_string);
          });
        }
      });
    });
  }

  public void removeRequestHandler(@NotNull CefRequestHandler handler, @NotNull CefBrowser browser) {
    myRequestHandler.remove(handler, browser, () -> myCefClient.removeRequestHandler());
  }

  public void removeAllHandlers(CefBrowser browser) {
    myContextMenuHandler.removeAll(browser);
    myDialogHandler.removeAll(browser);
    myDisplayHandler.removeAll(browser);
    myDownloadHandler.removeAll(browser);
    myDragHandler.removeAll(browser);
    myPermissionHandler.removeAll(browser);
    myFocusHandler.removeAll(browser);
    myJSDialogHandler.removeAll(browser);
    myKeyboardHandler.removeAll(browser);
    myLifeSpanHandler.removeAll(browser);
    myLoadHandler.removeAll(browser);
    myRequestHandler.removeAll(browser);
  }

  private class HandlerSupport<T> {
    private volatile Map<CefBrowser, List<T>> myMap;

    private synchronized void syncInitMap() {
      if (myMap == null) {
        myMap = Collections.synchronizedMap(new LinkedHashMap<>());
      }
    }

    private synchronized List<T> syncInitList(@NotNull CefBrowser browser, @NotNull Runnable onInit) {
      List<T> list = myMap.get(browser);
      if (list == null) {
        if (myMap.isEmpty()) {
          onInit.run();
        }
        myMap.put(browser, list = Collections.synchronizedList(new LinkedList<>()));
      }
      return list;
    }

    private synchronized void syncRemoveFromMap(@NotNull List<T> list, @NotNull CefBrowser browser, @NotNull Runnable onClear) {
      if (list.isEmpty()) {
        myMap.remove(browser);
        if (myMap.isEmpty()) {
          onClear.run();
        }
      }
    }

    public JBCefClient add(@NotNull T handler, @NotNull CefBrowser browser, @NotNull Runnable onInit) {
      if (myMap == null) {
        syncInitMap();
      }
      List<T> list = myMap.get(browser);
      if (list == null) {
        list = syncInitList(browser, onInit);
      }
      list.add(handler);
      return JBCefClient.this;
    }

    public void remove(@NotNull T handler, @NotNull CefBrowser browser, @NotNull Runnable onClear) {
      if (myMap != null) {
        List<T> list = myMap.get(browser);
        if (list != null) {
          list.remove(handler);
          if (list.isEmpty()) {
            syncRemoveFromMap(list, browser, onClear);
          }
        }
      }
    }

    public void clear() {
      if (myMap != null) {
        myMap.clear();
      }
    }

    public void removeAll(CefBrowser browser) {
      if (myMap != null) {
        myMap.remove(browser);
      }
    }

    public @Nullable List<T> get(@NotNull CefBrowser browser) {
      return myMap != null ? myMap.get(browser) : null;
    }

    public void handleAll(@NotNull CefBrowser browser, @NotNull HandlerRunnable<T> runnable) {
      List<T> list = get(browser);
      if (list == null) return;
      list.forEach(handler -> runnable.handle(handler));
    }

    public boolean handleBooleanReturnAnyOf(@NotNull CefBrowser browser, @NotNull HandlerCallable<T, Boolean> callable) {
      List<T> list = get(browser);
      if (list == null) return false;
      boolean result = false;
      for (T handler: list) {
        result |= Boolean.TRUE.equals(callable.handle(handler));
      }
      return result;
    }

    public boolean handleBooleanFirst(@NotNull CefBrowser browser, @NotNull HandlerCallable<T, Boolean> callable) {
      List<T> list = get(browser);
      if (list == null) return false;
      boolean result = false;
      for (T handler: list) {
        if (Boolean.TRUE.equals(callable.handle(handler))) {
          return true;
        }
      }

      return false;
    }

    public <R> R handleFirst(@NotNull CefBrowser browser, @NotNull HandlerCallable<T, R> callable) {
      List<T> list = get(browser);
      if (list == null) return null;
      for (T handler: list) {
        R result = callable.handle(handler);
        if (result != null) {
          return result;
        }
      }

      return null;
    }
  }

  private interface HandlerCallable<T, R> {
    @Nullable
    R handle(T handler);
  }

  private interface HandlerRunnable<T> {
    void handle(T handler);
  }
}
