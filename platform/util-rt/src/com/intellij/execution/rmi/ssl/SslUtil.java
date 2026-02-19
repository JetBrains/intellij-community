// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class SslUtil {
  public static final String SSL_CA_CERT_PATH = "sslCaCertPath";
  public static final String SSL_CLIENT_CERT_PATH = "sslClientCertPath";
  public static final String SSL_CLIENT_KEY_PATH = "sslClientKeyPath";
  public static final String SSL_TRUST_EVERYBODY = "sslTrustEverybody";
  public static final String SSL_USE_FACTORY = "sslUseFactory";

  private static final Map<String, Pair<Long, List<? extends SslEntityReader.Entity>>> entityCache = new ConcurrentHashMap<>();

  @NotNull
  private static List<? extends SslEntityReader.Entity> readWithCache(@NotNull String path) throws IOException {
    File file = new File(path);
    long stamp = file.lastModified();
    Pair<Long, List<? extends SslEntityReader.Entity>> cached = entityCache.get(path);
    if (cached != null && cached.first.equals(stamp)) {
      return cached.second;
    }
    try (FileInputStream stream = new FileInputStream(file)) {
      List<? extends SslEntityReader.Entity> res = SslEntityReader.getInstance().read(stream);
      entityCache.put(path, new Pair<Long, List<? extends SslEntityReader.Entity>>(stamp, res));
      return res;
    }
  }

  @TestOnly
  public static void resetCache() {
    entityCache.clear();
  }

  @NotNull
  public static List<X509Certificate> loadCertificates(@NotNull String caCertPath) throws IOException, CertificateException {
    return loadCertificates(readWithCache(caCertPath));
  }

  @NotNull
  public static X509Certificate readCertificate(@NotNull String filePath) throws CertificateException, IOException {
    List<X509Certificate> certificates = loadCertificates(filePath);
    if (certificates.isEmpty()) {
      throw new IOException("Certificate not found");
    }
    return certificates.get(0);
  }


  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath) throws IOException {
    return readPrivateKey(filePath, null);
  }

  @NotNull
  private static List<X509Certificate> loadCertificates(@NotNull List<? extends SslEntityReader.Entity> entities) throws IOException {
    List<X509Certificate> certs = new ArrayList<>();
    for (SslEntityReader.Entity entity : entities) {
      if (entity instanceof SslEntityReader.CertificateEntity) {
        certs.add(((SslEntityReader.CertificateEntity)entity).get());
      }
    }
    return certs;
  }

  @NotNull
  public static Pair<PrivateKey, List<X509Certificate>> readPrivateKeyAndCertificate(@NotNull String filePath, char @Nullable [] password) throws IOException {
    return loadPrivateKeyAndCerts(readWithCache(filePath), filePath, password);
  }

  @NotNull
  private static Pair<PrivateKey, List<X509Certificate>> loadPrivateKeyAndCerts(
    @NotNull List<? extends SslEntityReader.Entity> entities, @NotNull String filePath, char[] password)
    throws IOException {
    PrivateKey key = null;
    List<X509Certificate> certs = new ArrayList<>();
    for (SslEntityReader.Entity entity : entities) {
      if (entity instanceof SslEntityReader.EncryptedPrivateKeyEntity) {
        key = ((SslEntityReader.EncryptedPrivateKeyEntity)entity).get(password);
      }
      else if (entity instanceof SslEntityReader.UnencryptedPrivateKeyEntity) {
        key = ((SslEntityReader.UnencryptedPrivateKeyEntity)entity).get();
      }
      else if (entity instanceof SslEntityReader.CertificateEntity) {
        certs.add(((SslEntityReader.CertificateEntity)entity).get());
      }
    }
    if (key == null) {
      throw new IOException("Failed to find key in file " + filePath);
    }
    return Pair.create(key, certs.isEmpty() ? null : certs);
  }

  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath, char @Nullable [] password) throws IOException {
    return readPrivateKeyAndCertificate(filePath, password).first;
  }

  static class TrustEverybodyManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
    }

    @Override
    public X509Certificate @NotNull [] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
