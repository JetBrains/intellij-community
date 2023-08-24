// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SslTrustStore extends DelegateKeyStore {
  private static final Logger LOG = Logger.getLogger(SslTrustStore.class.getCanonicalName());
  private static final String NAME = "idea-trust-store";
  static {
    ourProvider.setProperty("KeyStore." + NAME, SslTrustStore.class.getName());
  }

  private static final List<Pair<String, ? extends Certificate>> ourAdded = new ArrayList<>();
  private int myAdded;

  public SslTrustStore() {
    super(KeyStore.getDefaultType());
  }

  public static String getType() {
    return NAME;
  }

  public static void setDefault() {
    System.setProperty("javax.net.ssl.trustStoreType", NAME);
    if (System.getProperty("javax.net.ssl.trustStore") == null) {
      System.setProperty("javax.net.ssl.trustStore", getDefaultKeyStorePath());
    }
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    super.engineLoad(null, null);
  }

  @Override
  protected void validate(KeyStore keyStore) {
    super.validate(keyStore);
    for (int i = myAdded, sz = ourAdded.size(); i < sz; ++i) {
      Pair<String, ? extends Certificate> cert = ourAdded.get(i);
      try {
        keyStore.setCertificateEntry(cert.first, cert.second);
      }
      catch (KeyStoreException e) {
        throw new IllegalStateException(e);
      }
      myAdded = i + 1;
    }
  }

  public static int appendUserCert(@NotNull String alias, @NotNull String path) {
    try {
      List<X509Certificate> certs = SslUtil.loadCertificates(path);
      appendCertificates(alias, certs);
      return certs.size();
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void appendCertificates(@NotNull String alias, @NotNull Collection<? extends Certificate> certificates) {
    int i = 0;
    for (Certificate certificate : certificates) {
      appendCertificate(i == 0 ? alias : alias + "-" + i, certificate);
      ++i;
    }
  }

  public static void appendCertificate(@NotNull String alias, @NotNull Certificate certificate) {
    ourAdded.add(Pair.create(alias, certificate));
    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("Added certificate: " + alias + "\n" + certificate);
    }
  }

  public static int appendUserTrustStore(@NotNull String path, char[] password) {
    try {
      KeyStore tmpStore = loadKeyStore(path, password);
      if (tmpStore == null) return 0;
      int cnt = 0;
      for (Enumeration<String> aliases = tmpStore.aliases(); aliases.hasMoreElements(); ) {
        String alias = aliases.nextElement();
        Certificate certificate = tmpStore.getCertificate(alias);
        if (certificate == null) continue;
        ourAdded.add(Pair.create(alias, certificate));
        ++cnt;
      }
      return cnt;
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
