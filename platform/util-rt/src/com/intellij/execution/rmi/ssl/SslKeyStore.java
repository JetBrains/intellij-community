// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public final class SslKeyStore extends DelegateKeyStore {
  public static final String SSL_DEFERRED_KEY_LOADING = "sslDeferredKeyLoading";
  public static final String SSL_DEFERRED_CA_LOADING = "sslDeferredCaLoading";
  public static final String NAME = "idea-key-store";
  private static final List<KeyEntry> ourAdded = new ArrayList<>();
  private int myAdded;
  static {
    ourProvider.setProperty("KeyStore." + NAME, SslKeyStore.class.getName());
  }

  public SslKeyStore() {
    super("PKCS12");
  }

  public static void setDefault() {
    System.setProperty("javax.net.ssl.keyStoreType", NAME);
    if (System.getProperty("javax.net.ssl.keyStore") == null) {
      System.setProperty("javax.net.ssl.keyStore", getDefaultKeyStorePath());
    }
  }

  @Nullable
  public static PrivateKey getUserKey() {
    return ourAdded.isEmpty() ? null : ourAdded.get(0).key;
  }

  public static void loadKey(@NotNull String alias,
                             @NotNull String clientKeyPath,
                             @Nullable String clientCertPath,
                             @Nullable char[] password) {
    try {
      PrivateKey key = SslUtil.readPrivateKey(clientKeyPath, password);
      List<X509Certificate> certificates = clientCertPath == null ? null : SslUtil.loadCertificates(clientCertPath);
      ourAdded.add(new KeyEntry(alias, key, certificates == null ? null : certificates.toArray(new Certificate[0])));
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }


  @Override
  protected void validate(KeyStore keyStore) {
    super.validate(keyStore);
    for (int i = myAdded, sz = ourAdded.size(); i < sz; ++i) {
      KeyEntry entry = ourAdded.get(i);
      if (entry.certChain != null) {
        try {
          keyStore.setKeyEntry(entry.alias, entry.key, null, entry.certChain);
        }
        catch (KeyStoreException e) {
          throw new IllegalStateException(e);
        }
      }
      myAdded = i + 1;
    }
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    super.engineLoad(null, null);
  }

  private static class KeyEntry {
    private final String alias;
    private final PrivateKey key;
    private final Certificate[] certChain;

    private KeyEntry(@NotNull String alias, @NotNull PrivateKey key, @Nullable Certificate[] certChain) {
      this.alias = alias;
      this.key = key;
      this.certChain = certChain;
    }
  }
}
