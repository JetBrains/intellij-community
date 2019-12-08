// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class SslKeyStore extends DelegateKeyStore {
  private static final String NAME = "idea-key-store";
  static {
    ourProvider.setProperty("KeyStore." + NAME, SslKeyStore.class.getName());
  }

  public SslKeyStore() {
    super("PKCS12");
  }

  public static void setDefault() {
    System.setProperty("javax.net.ssl.keyStoreType", NAME);
  }

  private void appendUserCert() {
    try {
      X509Certificate cert = SslSocketFactory.readCertificate(System.getProperty(SslSocketFactory.SSL_CLIENT_CERT_PATH));
      //delegate.setCertificateEntry("user-provided-cert", cert);
      PrivateKey key = SslSocketFactory.readPrivateKey(System.getProperty(SslSocketFactory.SSL_CLIENT_KEY_PATH));
      delegate.setKeyEntry("user-provided-key", key, null, new Certificate[]{cert});
    }
    catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    delegate.load(null, null);
    appendUserCert();
  }
}
