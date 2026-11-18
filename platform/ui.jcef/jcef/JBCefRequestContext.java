// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import org.cef.CefApp;
import org.cef.browser.CefRequestContext;
import org.cef.browser.CefRequestContextSettings;
import org.cef.callback.CefCompletionCallback;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An application-owned browser profile. In-memory profiles are accessible only through their
 * {@code JBCefRequestContext} instance. Browsers created with this context use separate CEF handles
 * that share this context's storage.
 */
@ApiStatus.Experimental
public final class JBCefRequestContext implements Disposable {
  private final @NotNull JBCefApp myApp;
  private final @Nullable Path myCachePath;
  private final Object myLock = new Object();
  private @Nullable CefRequestContext myCefRequestContext;
  private @Nullable JBCefCookieManager myCookieManager;
  private boolean myDisposed;

  JBCefRequestContext(@NotNull JBCefApp app, @Nullable Path cachePath) {
    myApp = app;
    myCachePath = cachePath;
    myCefRequestContext = createAnchor();
  }

  /**
   * Returns the on-disk cache path, or {@code null} for an in-memory profile.
   */
  public @Nullable String getCachePath() {
    return myCachePath == null ? null : myCachePath.toString();
  }

  public boolean isInMemory() {
    return myCachePath == null;
  }

  public boolean isDisposed() {
    synchronized (myLock) {
      return myDisposed;
    }
  }

  @Override
  public @NotNull String toString() {
    return "JBCefRequestContext[" + (myCachePath == null ? "in-memory" : myCachePath) + "]";
  }

  boolean isOwnedBy(@NotNull JBCefApp app) {
    return myApp == app;
  }

  /**
   * Returns the cookie manager for this profile.
   */
  public @NotNull JBCefCookieManager getCookieManager() {
    synchronized (myLock) {
      if (myDisposed) throw new IllegalStateException("Request context is disposed");
      if (myCookieManager == null) {
        CefCookieManager cefCookieManager = getCefRequestContext().getCookieManager();
        myCookieManager = new JBCefCookieManager(Objects.requireNonNull(cefCookieManager));
      }
      return myCookieManager;
    }
  }

  /**
   * Clears the HTTP cache for this profile.
   */
  public @NotNull CompletableFuture<Void> clearHttpCache() {
    return runWhenInitialized(CefRequestContext::clearHttpCache);
  }

  /**
   * Clears HTTP authentication credentials for this profile.
   */
  public @NotNull CompletableFuture<Void> clearHttpAuthCredentials() {
    return runWhenInitialized(CefRequestContext::clearHttpAuthCredentials);
  }

  /**
   * Clears certificate exceptions for this profile.
   */
  public @NotNull CompletableFuture<Void> clearCertificateExceptions() {
    return runWhenInitialized(CefRequestContext::ClearCertificateExceptions);
  }

  /**
   * Closes all network connections for this profile.
   */
  public @NotNull CompletableFuture<Void> closeAllConnections() {
    return runWhenInitialized(CefRequestContext::CloseAllConnections);
  }

  @ApiStatus.Internal
  public @NotNull CefRequestContext getCefRequestContext() {
    synchronized (myLock) {
      if (myDisposed) throw new IllegalStateException("Request context is disposed");
      return Objects.requireNonNull(myCefRequestContext, "Request context is not initialized");
    }
  }

  @NotNull CefRequestContext cloneCefRequestContext() {
    synchronized (myLock) {
      if (myDisposed) throw new IllegalStateException("Request context is disposed");
      CefRequestContext browserContext = CefRequestContext.createContext(
        Objects.requireNonNull(myCefRequestContext, "Request context is not initialized"), null);
      return Objects.requireNonNull(browserContext, "Failed to create a browser request context");
    }
  }

  // Creates a new internal CefRequestContext. This function helps to survive the CefApp restart.
  void resetCefContext() {
    synchronized (myLock) {
      if (!myDisposed) {
        myCefRequestContext = createAnchor();
      }
    }
  }

  // Disposes the internal CefRequestContext. This function helps to survive the CefApp restart.
  void releaseCefContext() {
    CefRequestContext context;
    JBCefCookieManager cookieManager;
    synchronized (myLock) {
      context = myCefRequestContext;
      cookieManager = myCookieManager;
      myCefRequestContext = null;
      myCookieManager = null;
    }
    if (cookieManager != null) cookieManager.getCefCookieManager().dispose();
    if (context != null) context.dispose();
  }

  @Override
  public void dispose() {
    myApp.disposeRequestContext(this);
  }

  void disposeFromApp() {
    synchronized (myLock) {
      if (myDisposed) return;
      myDisposed = true;
      if (myCefRequestContext != null) myCefRequestContext.dispose();
      if (myCookieManager != null) myCookieManager.getCefCookieManager().dispose();
      myCefRequestContext = null;
      myCookieManager = null;
    }
  }

  /**
   * Creates an anchor instance of CefRequestContext. This instance is not use by any browser directly. To set it
   * as the browser request context it must be cloned.
   */
  private @NotNull CefRequestContext createAnchor() {
    if (myCachePath == null) {
      return Objects.requireNonNull(CefRequestContext.createContext((CefRequestContextSettings)null, null));
    }
    CefRequestContextSettings settings = new CefRequestContextSettings();
    settings.cache_path = myCachePath.toString();
    return Objects.requireNonNull(CefRequestContext.createContext(settings, null));
  }

  private @NotNull CompletableFuture<Void> runWhenInitialized(@NotNull ContextOperation operation) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    if (isDisposed()) return CompletableFuture.failedFuture(new IllegalStateException("Request context is disposed"));

    CefApp.getInstance().onInitialization(state -> {
      if (state != CefApp.CefAppState.INITIALIZED) {
        future.completeExceptionally(new IllegalStateException("CEF did not initialize: " + state));
        return;
      }
      try {
        operation.run(getCefRequestContext(), () -> future.complete(null));
      }
      catch (RuntimeException e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  @FunctionalInterface
  private interface ContextOperation {
    void run(@NotNull CefRequestContext context, @NotNull CefCompletionCallback callback);
  }
}