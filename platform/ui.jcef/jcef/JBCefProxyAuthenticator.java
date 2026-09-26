// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.credentialStore.Credentials;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.ProxyAuthentication;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;

final class JBCefProxyAuthenticator {
  static @Nullable Credentials getCredentials(@NotNull String _proxyServer, int proxyPort) {
    var proxySettings = JBCefProxySettings.getInstance();
    var proxyServer = StringUtil.trimTrailing(_proxyServer, '/');

    // first try credentials from the settings
    if (
      proxySettings.configuration instanceof ProxyConfiguration.StaticProxyConfiguration http &&
      proxyServer.equals(StringUtil.trimTrailing(http.getHost(), '/')) &&
      http.getPort() == proxyPort
    ) {
      var credentials = proxySettings.credentials;
      if (credentials != null && credentials.getUserName() != null) {
        return credentials;
      }
    }

    // then ask the user for credentials
    var credentials = new Ref<Credentials>();
    if (!GraphicsEnvironment.isHeadless()) {
      var runnable = (Runnable)() -> {
        credentials.set(ProxyAuthentication.getInstance().getPromptedAuthentication(IdeBundle.message("prompt.jcef.proxy"), proxyServer, proxyPort));
      };
      if (EDT.isCurrentThreadEdt()) {
        runnable.run();
      }
      else {
        try {
          EventQueue.invokeAndWait(runnable);
        }
        catch (Throwable e) {
          Logger.getInstance(JBCefProxyAuthenticator.class).error(e);
        }
      }
    }
    return credentials.get();
  }
}
