// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public final class SslUtil {
  public static final String SSL_CA_CERT_PATH = "sslCaCertPath";
  public static final String SSL_CLIENT_CERT_PATH = "sslClientCertPath";
  public static final String SSL_CLIENT_KEY_PATH = "sslClientKeyPath";
  public static final String SSL_TRUST_EVERYBODY = "sslTrustEverybody";
  public static final String SSL_USE_FACTORY = "sslUseFactory";

  @NotNull
  public static List<X509Certificate> loadCertificates(@NotNull String caCertPath)
    throws IOException, CertificateException {
    return SslEntityReader.getInstance().loadCertificates(caCertPath);
  }

  @NotNull
  public static InputStream stringStream(@NotNull String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }

  @NotNull
  public static X509Certificate readCertificate(@NotNull String filePath) throws CertificateException, IOException {
    return SslEntityReader.getInstance().readCertificate(new FileInputStream(filePath));
  }

  @NotNull
  public static X509Certificate readCertificateFromString(@NotNull String s) throws CertificateException, IOException {
    return SslEntityReader.getInstance().readCertificate(stringStream(s));
  }

  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath) throws IOException {
    return readPrivateKey(filePath, null);
  }

  @NotNull
  public static Pair<PrivateKey, List<X509Certificate>> readPrivateKeyAndCertificate(@NotNull String filePath, @Nullable char[] password) throws IOException {
    return SslEntityReader.getInstance().readPrivateKeyAndCertificate(filePath, password);
  }

  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath, @Nullable char[] password) throws IOException {
    return SslEntityReader.getInstance().readPrivateKey(filePath, password);
  }

  static class TrustEverybodyManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
    }

    @Override
    @NotNull
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
