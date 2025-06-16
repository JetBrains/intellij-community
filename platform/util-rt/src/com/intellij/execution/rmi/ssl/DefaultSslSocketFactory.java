// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

@ApiStatus.Internal
public final class DefaultSslSocketFactory extends DelegateSslSocketFactory {
  public DefaultSslSocketFactory() throws GeneralSecurityException {
    this(false);
  }

  public DefaultSslSocketFactory(boolean trustEveryone) throws GeneralSecurityException {
    super(trustEveryone ? createTrustEveryoneDelegate() : createDefaultDelegate());
  }

  public DefaultSslSocketFactory(String trustEveryone) throws GeneralSecurityException {
    this(Boolean.parseBoolean(trustEveryone));
  }

  @NotNull
  private static SSLSocketFactory createDefaultDelegate() throws NoSuchAlgorithmException {
    return SSLContext.getDefault().getSocketFactory();
  }
  @NotNull
  private static SSLSocketFactory createTrustEveryoneDelegate() throws GeneralSecurityException {
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    KeyStore ks = SslKeyStore.getInstance();
    try {
      ks.load(null, null);
    }
    catch (IOException e) {
      throw new GeneralSecurityException(e);
    }
    kmf.init(ks, null);
    KeyManager[] km = kmf.getKeyManagers();
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(km, new TrustManager[]{new SslUtil.TrustEverybodyManager()}, null);
    return ctx.getSocketFactory();
  }
}
