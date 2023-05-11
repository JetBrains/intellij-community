// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SslUtil {
  public static final String SSL_CA_CERT_PATH = "sslCaCertPath";
  public static final String SSL_CLIENT_CERT_PATH = "sslClientCertPath";
  public static final String SSL_CLIENT_KEY_PATH = "sslClientKeyPath";
  public static final String SSL_TRUST_EVERYBODY = "sslTrustEverybody";
  public static final String SSL_USE_FACTORY = "sslUseFactory";
  private static final String BEGIN_MARK = "-----BEGIN";

  @NotNull
  public static List<X509Certificate> loadCertificates(@NotNull String caCertPath)
    throws IOException, CertificateException {
    String string = FileUtilRt.loadFile(new File(caCertPath));
    List<X509Certificate> certs = new ArrayList<>();
    List<String> tokens = splitBundle(string);
    for (String token : tokens) {
      if (token == null || token.trim().isEmpty()) continue;
      certs.add(readCertificate(stringStream(token)));
    }
    return certs;
  }

  private static List<String> splitBundle(@NotNull String string) {
    int idx = string.indexOf(BEGIN_MARK);
    if (idx == -1) {
      return Collections.singletonList(string);
    }
    List<String> res = new ArrayList<>();
    while (idx != -1) {
      int endIdx = string.indexOf(BEGIN_MARK, idx + BEGIN_MARK.length());
      res.add(string.substring(idx, endIdx == -1 ? string.length() : endIdx));
      idx = endIdx;
    }
    return res;
  }

  @NotNull
  public static InputStream stringStream(@NotNull String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }

  @NotNull
  public static X509Certificate readCertificate(@NotNull String filePath) throws CertificateException, IOException {
    return readCertificate(new FileInputStream(filePath));
  }

  @NotNull
  public static X509Certificate readCertificate(@NotNull InputStream stream) throws CertificateException, IOException {
    X509Certificate certificate = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(stream);
    stream.close();
    return certificate;
  }

  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath) throws IOException {
    return readPrivateKey(filePath, null);
  }

  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath, @Nullable char[] password) throws IOException {
    return new PrivateKeyReader(filePath, password).getPrivateKey();
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
