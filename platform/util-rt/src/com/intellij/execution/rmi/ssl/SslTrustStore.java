// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.text.StringUtilRt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;

public final class SslTrustStore extends DelegateKeyStore {
  private static final String NAME = "idea-trust-store";
  static {
    ourProvider.setProperty("KeyStore." + NAME, SslTrustStore.class.getName());
  }

  public SslTrustStore() {
    super(KeyStore.getDefaultType());
  }

  public static void setDefault() {
    System.setProperty("javax.net.ssl.trustStoreType", NAME);
    if (System.getProperty("javax.net.ssl.trustStore") == null) {
      System.setProperty("javax.net.ssl.trustStore", getDefaultKeyStorePath());
    }
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    delegate.load(null, null);
    appendUserItems(delegate);
  }

  private static void appendUserCert(KeyStore keyStore, String alias, String path) {
    try {
      List<X509Certificate> certs = SslUtil.loadCertificates(path);
      for (int i = 0; i < certs.size(); i++) {
        keyStore.setCertificateEntry(i == 0 ? alias : alias + "-" + i, certs.get(i));
      }
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void appendUserTrustStore(KeyStore keyStore, String path, char[] password) {
    try {
      File file = new File(path);
      if (!file.exists()) return;
      KeyStore tmpStore = KeyStore.getInstance(KeyStore.getDefaultType());
      try (InputStream stream = new FileInputStream(file)) {
        tmpStore.load(stream, password);
      }
      for (Enumeration<String> aliases = tmpStore.aliases(); aliases.hasMoreElements(); ) {
        String alias = aliases.nextElement();
        Certificate certificate = tmpStore.getCertificate(alias);
        if (certificate == null) continue;
        keyStore.setCertificateEntry(alias, certificate);
      }
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void appendUserItems(KeyStore keyStore) {
    String userCert = System.getProperty(SslUtil.SSL_CA_CERT_PATH);
    if (!StringUtilRt.isEmpty(userCert)) {
      appendUserCert(keyStore, "user-provided-ca", userCert);
    }
    String trustStores = System.getProperty(SslUtil.SSL_TRUST_STORE_PATHS);
    if (!StringUtilRt.isEmpty(trustStores)) {
      String[] storesPaths = trustStores.split(File.pathSeparator);
      for (String path : storesPaths) {
        String pass = "changeit";
        char[] chars = new char[pass.length()];
        pass.getChars(0, pass.length(), chars, 0);
        appendUserTrustStore(keyStore, path, chars);
      }
    }
  }
}
