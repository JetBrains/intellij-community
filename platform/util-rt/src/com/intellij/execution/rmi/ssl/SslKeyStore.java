// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

public class SslKeyStore extends DelegateKeyStore {
  public static final String SSL_DEFERRED_KEY_LOADING = "sslDeferredKeyLoading";
  public static final String NAME = "idea-key-store";
  private static final Map<PrivateKey, X509Certificate> ourAutoAdded = new LinkedHashMap<PrivateKey, X509Certificate>();
  static {
    ourProvider.setProperty("KeyStore." + NAME, SslKeyStore.class.getName());
  }

  public SslKeyStore() {
    super("PKCS12");
    loadUserCert();
  }

  public static void setDefault() {
    System.setProperty("javax.net.ssl.keyStoreType", NAME);
    if (System.getProperty("javax.net.ssl.keyStore") == null) {
      System.setProperty("javax.net.ssl.keyStore", getDefaultKeyStorePath());
    }
  }

  private static void loadUserCert() {
    String certPath = System.getProperty(SslUtil.SSL_CLIENT_CERT_PATH);
    String keyPath = System.getProperty(SslUtil.SSL_CLIENT_KEY_PATH);
    if (certPath != null && keyPath != null) {
      try {
        loadKey(certPath, keyPath, null);
      }
      catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static void loadKey(@NotNull String clientCertPath,
                             @NotNull String clientKeyPath,
                             @Nullable char[] password) throws CertificateException, IOException {
    PrivateKey key = SslUtil.readPrivateKey(clientKeyPath, password);
    if (ourAutoAdded.containsKey(key)) return;
    X509Certificate cert = SslUtil.readCertificate(clientCertPath);
    ourAutoAdded.put(key, cert);
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    delegate.load(null, null);
    int i = 0;
    for (Map.Entry<PrivateKey, X509Certificate> entry : ourAutoAdded.entrySet()) {
      try {
        delegate.setKeyEntry("user-provided-key" + (i == 0 ? "" : "#" + i), entry.getKey(), null, new Certificate[]{entry.getValue()});
      }
      catch (KeyStoreException e) {
        throw new IllegalStateException();
      }
      ++i;
    }
  }
}
