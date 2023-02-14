// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.security;

import org.jetbrains.annotations.NotNull;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public final class CompositeX509TrustManager implements X509TrustManager {
  private final List<X509TrustManager> myManagers = new ArrayList<>();

  public CompositeX509TrustManager(@NotNull TrustManager[]... managerSets) {
    for (TrustManager[] set : managerSets) {
      for (TrustManager manager : set) {
        if (manager instanceof X509TrustManager) {
          myManagers.add((X509TrustManager)manager);
        }
      }
    }
  }

  @Override
  public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
    for (X509TrustManager manager : myManagers) {
      try {
        manager.checkServerTrusted(certificates, s);
        return;
      }
      catch (CertificateException ignored) { }
    }
    throw new CertificateException("No trusting managers found for " + s);
  }

  @Override
  @NotNull
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}