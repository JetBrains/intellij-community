// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public class SslTrustStore extends DelegateKeyStore {
  private static final String NAME = "idea-trust-store";
  static {
    ourProvider.setProperty("KeyStore." + NAME, SslTrustStore.class.getName());
  }

  public SslTrustStore() {super(KeyStore.getDefaultType());}

  public static void setDefault() {
    System.setProperty("javax.net.ssl.trustStoreType", NAME);
    if (System.getProperty("javax.net.ssl.trustStore") == null) {
      System.setProperty("javax.net.ssl.trustStore", getDefaultKeyStorePath());
    }
  }

  private void appendUserCert() {
    try {
      List<X509Certificate> certs = SslUtil.loadCertificates(System.getProperty(SslUtil.SSL_CA_CERT_PATH));
      for (int i = 0; i < certs.size(); i++) {
        delegate.setCertificateEntry("user-provided-ca" + (i == 0 ? "" : "-" + i), certs.get(i));
      }
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
