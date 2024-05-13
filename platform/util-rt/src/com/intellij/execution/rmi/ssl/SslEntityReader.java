// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SslEntityReader {
  private static final SslEntityReader DEFAULT = new SslEntityReader();

  private static final String BEGIN_MARK = "-----BEGIN";

  @NotNull
  public static SslEntityReader getInstance() {
    return DEFAULT;
  }

  @NotNull
  public Pair<PrivateKey, List<X509Certificate>> readPrivateKeyAndCertificate(@NotNull String filePath, @Nullable char[] password)
    throws IOException {
    return new PrivateKeyReader(filePath, password).getPrivateKeyAndCertificate();
  }

  @NotNull
  public List<X509Certificate> loadCertificates(@NotNull String caCertPath) throws CertificateException, IOException {
    String string = FileUtilRt.loadFile(new File(caCertPath));
    List<X509Certificate> certs = new ArrayList<>();
    List<String> tokens = splitBundle(string);
    for (String token : tokens) {
      if (token == null || token.trim().isEmpty()) continue;
      certs.add(SslUtil.readCertificateFromString(token));
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
  public X509Certificate readCertificate(@NotNull InputStream stream) throws CertificateException, IOException {
    X509Certificate certificate = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(stream);
    stream.close();
    return certificate;
  }

  @NotNull
  public PrivateKey readPrivateKey(@NotNull String filePath, @Nullable char[] password) throws IOException {
    return new PrivateKeyReader(filePath, password).getPrivateKey();
  }
}