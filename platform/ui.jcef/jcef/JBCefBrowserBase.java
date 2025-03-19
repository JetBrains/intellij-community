// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.credentialStore.Credentials;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextCache;
import com.intellij.util.IconUtil;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.net.ssl.CertificateListener;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.ui.UIUtil;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.network.CefCookieManager;
import org.cef.network.CefRequest;
import org.cef.security.CefSSLInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.intellij.ui.jcef.JBCefEventUtils.convertCefKeyEvent;
import static com.intellij.ui.jcef.JBCefEventUtils.isUpDownKeyEvent;
import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_USER_LAST;

/**
 * Base class for windowed and offscreen browsers.
 */
public abstract class JBCefBrowserBase implements JBCefDisposable {
  /**
   * @see #setProperty(String, Object)
   */
  public static class Properties {
    /**
     * Prevents the browser from providing credentials via the
     * {@link CefRequestHandler#getAuthCredentials(CefBrowser, String, boolean, String, int, String, String, CefAuthCallback)} callback.
     * <p>
     * Accepts {@link Boolean} values. Use the property to handle the callback on your own.
     */
    public static final @NotNull String NO_DEFAULT_AUTH_CREDENTIALS = "JBCefBrowserBase.noDefaultAuthCredentials";

    /**
     * Disables or enables a context menu on click.
     * <p>
     * Accepts {@link Boolean} values.
     */
    public static final @NotNull String NO_CONTEXT_MENU = "JBCefBrowserBase.noContextMenu";

    static {
      PropertiesHelper.setType(NO_DEFAULT_AUTH_CREDENTIALS, Boolean.class);
      PropertiesHelper.setType(NO_CONTEXT_MENU, Boolean.class);
    }
  }

  private static final Logger LOG = Logger.getInstance(JBCefBrowserBase.class);
  protected static final @NotNull String BLANK_URI = "about:blank";
  private static final @NotNull Icon ERROR_PAGE_ICON = AllIcons.General.ErrorDialog;

  @SuppressWarnings("SpellCheckingInspection")
  public static final @NotNull String JBCEFBROWSER_INSTANCE_PROP = "JBCefBrowser.instance";
  private final @NotNull DisposeHelper myDisposeHelper = new DisposeHelper();
  private final LoadDeferrer NO_LOADER = new LoadDeferrer(null, "");
  private final AtomicReference<LoadDeferrer> myLoadDeferrer = new AtomicReference<>(NO_LOADER);
  private @NotNull String myLastRequestedUrl = "";
  private @Nullable String myLoadingUrl = "";
  private final @NotNull Object myUrlLock = new Object();
  private volatile @Nullable ErrorPage myErrorPage;
  private final @NotNull PropertiesHelper myPropertiesHelper = new PropertiesHelper();
  private final @NotNull AtomicBoolean myIsCreateStarted = new AtomicBoolean(false);
  private @Nullable CefRequestHandler myHrefProcessingRequestHandler;

  private final @NotNull CertificateListener myCertificateListener;

  private static final LazyInitializer.LazyValue<@NotNull String> ERROR_PAGE_READER = LazyInitializer.create(() -> {
    try {
      return new String(FileUtil.loadBytes(Objects.requireNonNull(
        JBCefApp.class.getResourceAsStream("resources/load_error.html"))), StandardCharsets.UTF_8);
    }
    catch (IOException | NullPointerException e) {
      LOG.error("couldn't find load_error.html", e);
    }
    return "";
  });

  private static final LazyInitializer.LazyValue<ScaleContextCache<String>> BASE64_ERROR_PAGE_ICON = LazyInitializer.create(() -> {
    return new ScaleContextCache<>((scaleContext) -> {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        BufferedImage image = IconUtil.toBufferedImage(IconUtil.scale(ERROR_PAGE_ICON, scaleContext), false);
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
      }
      catch (IOException ex) {
        LOG.error("couldn't write an error image", ex);
      }
      return "";
    });
  });

  /**
   * According to
   * <a href="https://github.com/chromium/chromium/blob/55f44515cd0b9e7739b434d1c62f4b7e321cd530/third_party/blink/public/web/web_view.h#L191">SetZoomLevel</a>
   * docs, there is a geometric progression that starts with 0.0 and 1.2 common ratio.
   * Following functions provide API familiar to developers:
   *
   * @see #setZoomLevel(double)
   * @see #getZoomLevel()
   */
  private static final double ZOOM_COMMON_RATIO = 1.2;
  private static final double LOG_ZOOM = Math.log(ZOOM_COMMON_RATIO);

  static @Nullable WeakReference<JBCefBrowserBase> focusedBrowser;

  protected final @NotNull JBCefClient myCefClient;
  private final boolean myDefaultCefClient;
  protected final @NotNull CefBrowser myCefBrowser;
  private final boolean myIsOffScreenRendering;
  private final boolean myEnableOpenDevToolsMenuItem;
  private final @Nullable CefLoadHandler myLoadHandler;
  private final @Nullable CefRequestHandler myRequestHandler;
  private final @Nullable CefContextMenuHandler myContextMenuHandler;
  private final @NotNull ReentrantLock myCookieManagerLock = new ReentrantLock();
  private volatile @Nullable JBCefCookieManager myJBCefCookieManager;
  private volatile @Nullable String myCssBgColor;
  private @Nullable JDialog myDevtoolsFrame = null;

  private final @NotNull CefKeyboardHandler myKeyboardHandler;

  /**
   * The browser instance is disposed automatically with {@link JBCefClient}
   * as the parent {@link Disposable} (see {@link #getJBCefClient()}).
   * Nevertheless, it can be disposed manually as well when necessary.
   */
  protected JBCefBrowserBase(@NotNull JBCefBrowserBuilder builder) {
    JBCefClient providedClient = builder.myClient;
    if (providedClient == null) {
      myCefClient = JBCefApp.getInstance().createClient();
      myDefaultCefClient = true;
    }
    else {
      myCefClient = providedClient;
      myDefaultCefClient = false;
    }
    Disposer.register(myCefClient, this);

    myIsOffScreenRendering = builder.myIsOffScreenRendering;
    myEnableOpenDevToolsMenuItem = builder.myEnableOpenDevToolsMenuItem;
    boolean isDefaultBrowserCreated = false;
    CefBrowser cefBrowser = builder.myCefBrowser;

    if (cefBrowser == null) {
      if (myIsOffScreenRendering && getCefDelegate() == null) {
        JBCefApp.checkOffScreenRenderingModeEnabled();
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = builder.myWindowlessFrameRate;
        @NotNull JBCefOSRHandlerFactory factory = ObjectUtils.notNull(builder.myOSRHandlerFactory, JBCefOSRHandlerFactory.getInstance());
        cefBrowser = createOsrBrowser(factory, myCefClient.getCefClient(), builder.myUrl, null, null, null, builder.myMouseWheelEventEnable,
                                      settings);
      }
      else {
        cefBrowser = myCefClient.getCefClient().createBrowser(validateUrl(builder.myUrl), CefRendering.DEFAULT, false, null);
      }
      isDefaultBrowserCreated = true;
    }
    myCefBrowser = cefBrowser;

    if (isDefaultBrowserCreated) {
      myCefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
        @Override
        public void onAfterCreated(CefBrowser browser) {
          LoadDeferrer loader = myLoadDeferrer.getAndSet(null);
          if (loader != null && loader != NO_LOADER) {
            loader.load();
          }
        }

        @Override
        public void onBeforeClose(CefBrowser browser) {
          // Release all references to the browser and clean up the references list.
          // It's expected to be called by method JBCefClient#HandlerSupport<CefLifeSpanHandler>#handle() that keeps the reference to the
          // clients handlers list until all handlers are used.
          myCefClient.removeAllHandlers(getCefBrowser());
        }
      }, getCefBrowser());

      myCefClient.addLoadHandler(myLoadHandler = new CefLoadHandlerAdapter() {
        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
          setPageBackgroundColor();
        }

        @Override
        public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
          // do not show error page if another URL has already been requested to load
          ErrorPage errorPage = myErrorPage;
          String lastRequestedUrl = getLastRequestedUrl();
          if (errorPage != null && lastRequestedUrl.equals(failedUrl)) {
            String html = errorPage.create(errorCode, errorText, failedUrl);
            if (html != null) UIUtil.invokeLaterIfNeeded(() -> compareLastRequestedUrlAndPerform(failedUrl, () -> loadHTML(html)));
          }
        }
      }, getCefBrowser());

      myCefClient.addRequestHandler(myRequestHandler = new CefRequestHandlerAdapter() {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser,
                                      CefFrame frame,
                                      CefRequest request,
                                      boolean user_gesture,
                                      boolean is_redirect) {
          setLastRequestedUrl(ObjectUtils.notNull(request.getURL(), ""));
          return super.onBeforeBrowse(browser, frame, request, user_gesture, is_redirect);
        }

        @Override
        public boolean getAuthCredentials(CefBrowser browser,
                                          String origin_url,
                                          boolean isProxy,
                                          String host,
                                          int port,
                                          String realm,
                                          String scheme, CefAuthCallback callback) {
          if (isProxy && !isProperty(Properties.NO_DEFAULT_AUTH_CREDENTIALS)) {
            Credentials credentials = JBCefProxyAuthenticator.getCredentials(JBCefBrowserBase.this, host, port);
            if (credentials != null) {
              callback.Continue(credentials.getUserName(), credentials.getPasswordAsString());
              return true;
            }
            LOG.error("missing credentials to sign in to proxy");
          }
          return super.getAuthCredentials(browser, origin_url, isProxy, host, port, realm, scheme, callback);
        }

        @Override
        public boolean onCertificateError(CefBrowser browser,
                                          CefLoadHandler.ErrorCode cert_error,
                                          String request_url,
                                          CefSSLInfo sslInfo,
                                          CefCallback callback) {
          ApplicationManager.getApplication().invokeLater(() -> {
            try {
              CertificateManager.getInstance().getTrustManager().checkServerTrusted(sslInfo.certificate.getCertificatesChain(), "UNKNOWN");
              callback.Continue();
            }
            catch (CertificateException e) {
              callback.cancel();
            }
          });
          return true;
        }
      }, getCefBrowser());

      myCefClient.addContextMenuHandler(myContextMenuHandler = createDefaultContextMenuHandler(), getCefBrowser());
    }
    else {
      myLoadHandler = null;
      myRequestHandler = null;
      myContextMenuHandler = null;
    }

    if (builder.myCreateImmediately) createImmediately();

    myCertificateListener = new CertificateListener() {
      @Override
      public void certificateAdded(X509Certificate certificate) { }

      @Override
      public void certificateRemoved(X509Certificate certificate) {
        CefRequestContext context = getCefBrowser().getRequestContext();
        if (context != null) {
          context.ClearCertificateExceptions(null);
          context.CloseAllConnections(null);
        }
        else {
          getCefBrowser().createImmediately();
          // It's a trick to wait until the browser is ready. TODO: introduce CefBrowser#getRequestContextAsync
          CompletableFuture<String> browserReadyFuture = getCefBrowser().getDevToolsClient().executeDevToolsMethod("");
          browserReadyFuture.thenRun(() -> {
            CefRequestContext context1 = Objects.requireNonNull(getCefBrowser().getRequestContext());
            context1.ClearCertificateExceptions(null);
            context1.CloseAllConnections(null);
          });
        }
      }
    };

    CertificateManager.getInstance().getCustomTrustManager().addListener(myCertificateListener);

    myCefClient.addKeyboardHandler(myKeyboardHandler = new CefKeyboardHandlerAdapter() {
      @Override
      public boolean onKeyEvent(CefBrowser browser, CefKeyEvent cefKeyEvent) {
        if (isOffScreenRendering()) return false;

        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean consume = focusOwner != browser.getUIComponent();
        if (consume && SystemInfo.isMac && isUpDownKeyEvent(cefKeyEvent)) return true; // consume

        Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (focusedWindow == null) {
          return true; // consume
        }
        try {
          KeyEvent javaKeyEvent = convertCefKeyEvent(cefKeyEvent, focusedWindow);
          Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(javaKeyEvent);
        } catch (IllegalArgumentException e) {
          LOG.error("Failed to convert CEF key event: " + cefKeyEvent + "\nReason: " + e);
        }

        return consume;
      }
    }, myCefBrowser);
  }

  /**
   * Creates the native browser.
   * <p>
   * Normally the native browser is created when the browser's component is added to a UI hierarchy.
   * <p>
   * Prefer this method to {@link CefBrowser#createImmediately}.
   */
  public void createImmediately() {
    synchronized (myIsCreateStarted) {
      myIsCreateStarted.set(true);
      getCefBrowser().createImmediately();
    }
  }

  /**
   * Loads URL.
   */
  public final void loadURL(@NotNull String url) {
    if (isCefBrowserCreated() || !scheduleLoading(new LoadDeferrer(null, url))) {
      loadUrlImpl(url);
    }
  }

  /**
   * Loads HTML content.
   * <p>
   * Registers a virtual resource containing {@code html} that will be
   * available in the browser at {@code url} and loads {@code url}.
   *
   * @param html content to load
   * @param url  the URL
   */
  public final void loadHTML(@NotNull String html, @NotNull String url) {
    if (isCefBrowserCreated() || !scheduleLoading(new LoadDeferrer(html, url))) {
      loadHtmlImpl(html, url);
    }
  }

  // returns true if loading is scheduled for when the browser will finish initialization
  private boolean scheduleLoading(@NotNull LoadDeferrer loader) {
    return myLoadDeferrer.getAndUpdate(value -> value == null ? null : loader) != null;
  }

  /**
   * Loads HTML content.
   */
  public final void loadHTML(@NotNull String html) {
    loadHTML(html, BLANK_URI);
  }

  public final @NotNull CefBrowser getCefBrowser() {
    return myCefBrowser;
  }

  /**
   * Returns the browser currently in focus.
   * <p>
   * It is possible that at a certain moment, the browser can be focused natively but can not yet have Java focus.
   */
  public static @Nullable JBCefBrowserBase getFocusedBrowser() {
    if (focusedBrowser != null) {
      return focusedBrowser.get();
    }
    return null;
  }

  /**
   * @return 1.0 is 100%
   * @see #ZOOM_COMMON_RATIO
   */
  @SuppressWarnings("unused")
  public final double getZoomLevel() {
    return Math.pow(ZOOM_COMMON_RATIO, myCefBrowser.getZoomLevel());
  }

  /**
   * @param zoomLevel 1.0 is 100%.
   * @see #ZOOM_COMMON_RATIO
   */
  @SuppressWarnings("unused")
  public final void setZoomLevel(double zoomLevel) {
    myCefBrowser.setZoomLevel(Math.log(zoomLevel) / LOG_ZOOM);
  }

  public final @NotNull JBCefClient getJBCefClient() {
    return myCefClient;
  }

  public static @NotNull JBCefCookieManager getGlobalJBCefCookieManager() {
    return new JBCefCookieManager(CefCookieManager.getGlobalManager());
  }

  public final @NotNull JBCefCookieManager getJBCefCookieManager() {
    myCookieManagerLock.lock();
    try {
      if (myJBCefCookieManager == null) {
        myJBCefCookieManager = new JBCefCookieManager();
      }
      return Objects.requireNonNull(myJBCefCookieManager);
    }
    finally {
      myCookieManagerLock.unlock();
    }
  }

  @SuppressWarnings("unused")
  public final void setJBCefCookieManager(@NotNull JBCefCookieManager jBCefCookieManager) {
    myCookieManagerLock.lock();
    try {
      myJBCefCookieManager = jBCefCookieManager;
    }
    finally {
      myCookieManagerLock.unlock();
    }
  }

  /**
   * Adds handler that opens any links clicked by user in external browser.
   */
  public void setOpenLinksInExternalBrowser(boolean openLinksInExternalBrowser) {
    if (openLinksInExternalBrowser) {
      enableExternalBrowserLinks();
    }
    else {
      disableExternalBrowserLinks();
    }
  }

  private void enableExternalBrowserLinks() {
    if (myHrefProcessingRequestHandler != null) return;
    var hrefProcessingRequestHandler = new CefRequestHandlerAdapter() {
      @Override
      public boolean onBeforeBrowse(CefBrowser browser,
                                    CefFrame frame,
                                    CefRequest request,
                                    boolean user_gesture,
                                    boolean is_redirect) {
        if (user_gesture) {
          BrowserUtil.open(request.getURL());
          return true;
        }
        return false;
      }
    };
    this.myCefClient.addRequestHandler(hrefProcessingRequestHandler, myCefBrowser);
    myHrefProcessingRequestHandler = hrefProcessingRequestHandler;
  }

  private void disableExternalBrowserLinks() {
    var hrefProcessingRequestHandler = myHrefProcessingRequestHandler;
    if (hrefProcessingRequestHandler != null) {
      myCefClient.removeRequestHandler(hrefProcessingRequestHandler, myCefBrowser);
      myHrefProcessingRequestHandler = null;
    }
  }

  /**
   * Disables navigation in the browser, initiated by user actions (clicks/gestures).
   * Equivalent to the following code, but also works in remote development environments:
   * <pre>{@code
   *  browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
   *      @Override
   *      public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
   *        return user_gesture;
   *      }
   *    }, browser.getCefBrowser());
   * }</pre>
   */
  public void disableNavigation() {
    CefDelegate delegate = getCefDelegate();
    if (delegate != null) {
      delegate.disableNavigation(myCefBrowser);
    }
    else {
      myCefClient.addRequestHandler(new CefRequestHandlerAdapter() {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
          return user_gesture;
        }
      }, myCefBrowser);
    }
  }

  /**
   * Returns the root browser component to be inserted into the UI.
   * This component adapts the internal JCEF component to IJ.
   */
  public abstract @Nullable JComponent getComponent();

  /**
   * Returns the actual browser component. This component is the target for events.
   */
  public @Nullable Component getBrowserComponent() {
    return myCefBrowser.getUIComponent();
  }

  /**
   * Returns whether the browser is rendered off-screen.
   *
   * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
   */
  public boolean isOffScreenRendering() {
    return myIsOffScreenRendering;
  }

  final boolean isCefBrowserCreated() {
    return isCefBrowserCreated(myCefBrowser);
  }

  static boolean isCefBrowserCreated(@NotNull CefBrowser cefBrowser) {
    CefDelegate delegate = getCefDelegate();
    if (delegate != null) {
      return delegate.isInitialized(cefBrowser);
    }
    return CefClient.isNativeBrowserCreated(cefBrowser);
  }

  static boolean isCefBrowserCreationStarted(@NotNull CefBrowser browser) {
    return CefClient.isNativeBrowserCreationStarted(browser);
  }

  /**
   * Returns whether {@link #createImmediately} has been called or the browser has already been created.
   * <p>
   * WARNING: Returns wrong result when {@link CefBrowser#createImmediately()} is called directly.
   */
  boolean isCefBrowserCreateStarted() {
    synchronized (myIsCreateStarted) {
      return myIsCreateStarted.get() || isCefBrowserCreated();
    }
  }

  /**
   * The method is thread safe.
   */
  @Override
  public void dispose() {
    dispose(null);
  }

  protected void dispose(@Nullable Runnable subDisposer) {
    myDisposeHelper.dispose(() -> {
      if (subDisposer != null) subDisposer.run();

      if (myLoadHandler != null) getJBCefClient().removeLoadHandler(myLoadHandler, getCefBrowser());
      if (myRequestHandler != null) getJBCefClient().removeRequestHandler(myRequestHandler, getCefBrowser());
      if (myHrefProcessingRequestHandler != null) getJBCefClient().removeRequestHandler(myHrefProcessingRequestHandler, getCefBrowser());
      if (myContextMenuHandler != null) getJBCefClient().removeContextMenuHandler(myContextMenuHandler, getCefBrowser());
      // There is also a CefLifeSpanHandler(see the class constructor) that has to remove himself at onBeforeClose()

      CertificateManager.getInstance().getCustomTrustManager().removeListener(myCertificateListener);

      myCefBrowser.stopLoad();
      myCefBrowser.setCloseAllowed();
      myCefBrowser.close(true);

      if (myDefaultCefClient) Disposer.dispose(myCefClient);

      myCefClient.removeKeyboardHandler(myKeyboardHandler, myCefBrowser);
    });
  }

  @Override
  public final boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  /**
   * Returns {@code JBCefBrowser} instance associated with this {@code CefBrowser}.
   */
  public static @Nullable JBCefBrowser getJBCefBrowser(@NotNull CefBrowser browser) {
    Component uiComp = browser.getUIComponent();
    if (uiComp != null) {
      Component parentComp = uiComp.getParent();
      if (parentComp instanceof JComponent) {
        return (JBCefBrowser)((JComponent)parentComp).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
      }
    }
    return null;
  }

  /**
   * Sets (overrides) background color in the HTML page.
   * <p>
   * The color is set for the currently displayed page and all the subsequently loaded pages.
   *
   * @param cssColor the color in CSS format
   * @see <a href="https://www.w3schools.com/cssref/css_colors_legal.asp">CSS color format</a>
   */
  public void setPageBackgroundColor(@NotNull String cssColor) {
    myCssBgColor = cssColor;
    setPageBackgroundColor();
  }

  private void setPageBackgroundColor() {
    if (myCssBgColor != null) {
      getCefBrowser().executeJavaScript("document.body.style.backgroundColor = \"" + myCssBgColor + "\";", BLANK_URI, 0);
    }
  }

  private void loadHtmlImpl(@NotNull String html, @NotNull String url) {
    loadUrlImpl(JBCefFileSchemeHandlerFactory.registerLoadHTMLRequest(getCefBrowser(), html, url));
  }

  private void loadUrlImpl(@NotNull String url) {
    synchronized (myUrlLock) {
      if (Objects.equals(myLoadingUrl, url)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("URL already requested, skipping: " + url);
        }
        return;
      }
      myLoadingUrl = url;
      setLastRequestedUrl(""); // will be set to a correct value in onBeforeBrowse()
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Loading URL: " + url);
    }
    getCefBrowser().loadURL(url);
  }

  private String getLastRequestedUrl() {
    synchronized (myUrlLock) {
      return myLastRequestedUrl;
    }
  }

  private void setLastRequestedUrl(@NotNull String url) {
    synchronized (myUrlLock) {
      myLastRequestedUrl = url;
    }
  }

  private void compareLastRequestedUrlAndPerform(@NotNull String url, @NotNull Runnable action) {
    synchronized (myUrlLock) {
      if (myLastRequestedUrl.equals(url)) action.run();
    }
  }

  /**
   * Used to create an error page.
   */
  public interface ErrorPage {
    /**
     * Default error page.
     */
    ErrorPage DEFAULT = new ErrorPage() {
      @Override
      public @NotNull String create(CefLoadHandler.@NotNull ErrorCode errorCode,
                                    @NotNull String errorText,
                                    @NotNull String failedUrl) {
        int fontSize = (int)(EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize() * 1.1);
        int headerFontSize = fontSize + JBUIScale.scale(3);
        int headerPaddingTop = headerFontSize / 5;
        int lineHeight = headerFontSize * 2;
        int iconPaddingRight = JBUIScale.scale(12);
        Color bgColor = JBColor.background();
        String bgWebColor = String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
        Color fgColor = JBColor.foreground();
        String fgWebColor = String.format("#%02x%02x%02x", fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue());

        String html = ERROR_PAGE_READER.get();
        html = html.replace("${lineHeight}", String.valueOf(lineHeight));
        html = html.replace("${iconPaddingRight}", String.valueOf(iconPaddingRight));
        html = html.replace("${fontSize}", String.valueOf(fontSize));
        html = html.replace("${headerFontSize}", String.valueOf(headerFontSize));
        html = html.replace("${headerPaddingTop}", String.valueOf(headerPaddingTop));
        html = html.replace("${bgWebColor}", bgWebColor);
        html = html.replace("${fgWebColor}", fgWebColor);
        html = html.replace("${errorText}", errorText);
        html = html.replace("${failedUrl}", failedUrl);

        ScaleContext ctx = ScaleContext.create();
        ctx.setScale(OBJ_SCALE.of(1.2 * headerFontSize / (float)ERROR_PAGE_ICON.getIconHeight()));
        // Reset sys scale to prevent raster downscaling on passing the image to jcef.
        // Overriding is used to prevent scale change during further intermediate context transformations.
        ctx.overrideScale(SYS_SCALE.of(1));

        html = html.replace("${base64Image}", ObjectUtils.notNull(BASE64_ERROR_PAGE_ICON.get().getOrProvide(ctx), ""));
        return html;
      }
    };

    /**
     * Returns an error page HTML.
     * <p>
     * To prevent showing the error page (e.g. filter out {@link CefLoadHandler.ErrorCode#ERR_ABORTED}) just return {@code null}.
     * To fallback to default error page return {@link ErrorPage#DEFAULT#create(CefLoadHandler.ErrorCode, String, String)}.
     */
    @Nullable
    String create(@NotNull @SuppressWarnings("unused") CefLoadHandler.ErrorCode errorCode,
                  @NotNull String errorText,
                  @NotNull String failedUrl);
  }

  /**
   * Sets the error page to display in the browser on load error.
   * <p>
   * By default, no error page is displayed. To enable displaying default error page pass {@link ErrorPage#DEFAULT}.
   * Passing {@code null} prevents the browser from displaying an error page.
   *
   * @param errorPage the error page producer, or {@code null}
   */
  public void setErrorPage(@Nullable ErrorPage errorPage) {
    myErrorPage = errorPage;
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
   * @return the passed boolean property value or {@code false} if it is not set or is not boolean
   */
  public boolean isProperty(@NotNull String name) {
    return myPropertiesHelper.is(name);
  }

  /**
   * @see #setProperty(String, Object)
   */
  @SuppressWarnings("unused")
  void addPropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertiesHelper.addPropertyChangeListener(name, listener);
  }

  /**
   * @see #setProperty(String, Object)
   */
  @SuppressWarnings("unused")
  void removePropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertiesHelper.removePropertyChangeListener(name, listener);
  }

  protected DefaultCefContextMenuHandler createDefaultContextMenuHandler() {
    return new DefaultCefContextMenuHandler();
  }

  protected class DefaultCefContextMenuHandler extends CefContextMenuHandlerAdapter {
    protected static final int DEBUG_COMMAND_ID = MENU_ID_USER_LAST;
    private final boolean isOpenDevToolsItemEnabled;

    public DefaultCefContextMenuHandler() {
      this.isOpenDevToolsItemEnabled = myEnableOpenDevToolsMenuItem || Registry.is("ide.browser.jcef.contextMenu.devTools.enabled");
    }

    public DefaultCefContextMenuHandler(boolean isOpenDevToolsItemEnabled) {
      this.isOpenDevToolsItemEnabled = isOpenDevToolsItemEnabled;
    }

    @Override
    public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
      if (isProperty(Properties.NO_CONTEXT_MENU)) {
        model.clear();
        return;
      }
      if (isOpenDevToolsItemEnabled) {
        model.addItem(DEBUG_COMMAND_ID, "Open DevTools");
      }
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
      if (isProperty(Properties.NO_CONTEXT_MENU)) {
        return false;
      }
      if (commandId == DEBUG_COMMAND_ID) {
        openDevtools();
        return true;
      }
      return false;
    }
  }

  public void openDevtools() {
    if (myDevtoolsFrame != null) {
      myDevtoolsFrame.setVisible(true);
      myDevtoolsFrame.toFront();
      return;
    }

    Component comp = getComponent();
    Window ancestor = comp == null ?
                      KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() :
                      SwingUtilities.getWindowAncestor(comp);

    if (ancestor == null) return;
    Rectangle bounds = ancestor.getGraphicsConfiguration().getBounds();

    myDevtoolsFrame = new JDialog(ancestor);
    myDevtoolsFrame.setTitle("JCEF DevTools");
    myDevtoolsFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    myDevtoolsFrame.setBounds(bounds.width / 4 + 100, bounds.height / 4 + 100, bounds.width / 2, bounds.height / 2);
    myDevtoolsFrame.setLayout(new BorderLayout());
    JBCefBrowser devTools = JBCefBrowser.createBuilder().setCefBrowser(myCefBrowser.getDevTools()).setClient(myCefClient).build();
    final Component devToolsBrowserComponent = devTools.getCefBrowser().getUIComponent();
    if (devToolsBrowserComponent instanceof JBCefOsrComponent)
      ((JBCefOsrComponent)devToolsBrowserComponent).setBrowser(devTools.getCefBrowser());

    myDevtoolsFrame.add(devTools.getComponent(), BorderLayout.CENTER);

    Disposer.register(this, devTools);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        if (myDevtoolsFrame != null) {
          myDevtoolsFrame.dispose();
          myDevtoolsFrame = null;
        }
      }
    });

    myDevtoolsFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        if (myDevtoolsFrame != null) {
          myDevtoolsFrame.dispose();
          myDevtoolsFrame = null;
        }
      }
    });

    myDevtoolsFrame.setVisible(true);
  }

  private final class LoadDeferrer {
    private final @Nullable String myHtml;
    private final @NotNull String myUrl;

    private LoadDeferrer(@Nullable String html, @NotNull String url) {
      myHtml = html;
      myUrl = url;
    }

    public void load() {
      // JCEF demands async loading.
      SwingUtilities.invokeLater(
        myHtml == null ?
        () -> loadUrlImpl(myUrl) :
        () -> loadHtmlImpl(myHtml, myUrl));
    }
  }

  private static @NotNull String validateUrl(@Nullable String url) {
    return url != null && !url.isEmpty() ? url : "";
  }

  private static @Nullable CefDelegate getCefDelegate() {
    return JBCefApp.getInstance().getDelegate();
  }

  private static CefRendering.CefRenderingWithHandler createCefRenderingWithHandler(@NotNull JBCefOSRHandlerFactory osrHandlerFactory, boolean isMouseWheelEventEnabled) {
    JComponent component = osrHandlerFactory.createComponent(isMouseWheelEventEnabled);
    CefRenderHandler handler = osrHandlerFactory.createCefRenderHandler(component);
    return new CefRendering.CefRenderingWithHandler(handler, component);
  }

  static @NotNull CefBrowser createOsrBrowser(@NotNull JBCefOSRHandlerFactory osrHandlerFactory,
                                              @NotNull CefClient client,
                                              @Nullable String url,
                                              @Nullable CefRequestContext context,
                                              @Nullable CefBrowser parentBrowser,
                                              @Nullable Point inspectAt,
                                              boolean isMouseWheelEventEnabled,
                                              CefBrowserSettings settings) {
    if (JBCefApp.isRemoteEnabled()) {
      Supplier<CefRendering> renderingSupplier = () -> createCefRenderingWithHandler(osrHandlerFactory, isMouseWheelEventEnabled);
      CefBrowser browser = client.createBrowser(url, renderingSupplier, true, context, settings);

      if (browser.getUIComponent() instanceof JBCefOsrComponent)
        ((JBCefOsrComponent)browser.getUIComponent()).setBrowser(browser);

      return browser;
    }

    final CefRendering.CefRenderingWithHandler rendering = createCefRenderingWithHandler(osrHandlerFactory, isMouseWheelEventEnabled);
    CefBrowserOsrWithHandler browser =
      new CefBrowserOsrWithHandler(client, ObjectUtils.notNull(url, ""), context, rendering.getRenderHandler(), rendering.getComponent(), parentBrowser, inspectAt, settings) {
        @Override
        protected CefBrowser createDevToolsBrowser(CefClient client,
                                                   String url,
                                                   CefRequestContext context,
                                                   CefBrowser parent,
                                                   Point inspectAt) {
          return createOsrBrowser(osrHandlerFactory, client, getUrl(), getRequestContext(), this, inspectAt, isMouseWheelEventEnabled,
                                  null);
        }
      };

    if (rendering.getComponent() instanceof JBCefOsrComponent)
      ((JBCefOsrComponent)rendering.getComponent()).setBrowser(browser);

    return browser;
  }
}
