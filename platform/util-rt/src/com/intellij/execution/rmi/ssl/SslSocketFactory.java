// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import com.intellij.security.CompositeX509TrustManager;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.intellij.execution.rmi.ssl.SslUtil.*;

@ApiStatus.Internal
public final class SslSocketFactory extends DelegateSslSocketFactory {
  public SslSocketFactory() throws GeneralSecurityException {
    super(createDelegate());
  }

  @NotNull
  private static SSLSocketFactory createDelegate() throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext ctx = SSLContext.getInstance("TLS");
    TrustManager[] tms;
    KeyManager[] kms;
    try {
      String caCertPath = System.getProperty(SSL_CA_CERT_PATH);
      String clientCertPath = System.getProperty(SSL_CLIENT_CERT_PATH);
      String clientKeyPath = System.getProperty(SSL_CLIENT_KEY_PATH);
      boolean trustEverybody = Boolean.parseBoolean(System.getProperty(SSL_TRUST_EVERYBODY));

      tms = trustEverybody ? new TrustManager[]{new TrustEverybodyManager()} :
            caCertPath == null ? new TrustManager[]{} : createTrustManagers(caCertPath);
      kms = clientCertPath != null && clientKeyPath != null
            ? new KeyManager[]{new MyKeyManager(clientCertPath, clientKeyPath)}
            : new KeyManager[]{};
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    ctx.init(kms, tms, null);
    return ctx.getSocketFactory();
  }

  public static TrustManager @NotNull [] createTrustManagers(@NotNull String caCertPath) throws Exception {
    List<X509Certificate> certs = loadCertificates(caCertPath);
    List<TrustManager> result = new ArrayList<>(certs.size());
    for (X509Certificate cert : certs) {
      result.add(new MyTrustManager(cert));
    }

    return new TrustManager[]{new CompositeX509TrustManager(result.toArray(new TrustManager[0]))};
  }

  private static final class MyTrustManager implements X509TrustManager {
    private X509TrustManager trustManager;

    private MyTrustManager(@NotNull X509Certificate caCertPath) throws Exception {
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(createStore(caCertPath));
      for (TrustManager tm : tmf.getTrustManagers()) {
        if (tm instanceof X509TrustManager) {
          trustManager = (X509TrustManager)tm;
          break;
        }
      }
      if (trustManager == null) {
        throw new RuntimeException("No X509TrustManager found");
      }
    }

    @NotNull
    private static KeyStore createStore(@NotNull X509Certificate certificate) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null);
      ks.setCertificateEntry(UUID.randomUUID().toString(), certificate);
      return ks;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      if (trustManager == null) throw new RuntimeException("No X509TrustManager found");
      trustManager.checkServerTrusted(x509Certificates, s);
    }

    @Override
    public X509Certificate @NotNull [] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  private static final class MyKeyManager extends X509ExtendedKeyManager {
    private final String myAlias = UUID.randomUUID().toString();
    private final X509Certificate @NotNull [] myCertificates;
    @NotNull private final PrivateKey myPrivateKey;

    private MyKeyManager(@NotNull String certPath, @NotNull String keyPath) throws Exception {
      myCertificates = new X509Certificate[]{readCertificate(certPath)};
      myPrivateKey = readPrivateKey(keyPath);
    }

    @Override
    public String @NotNull [] getClientAliases(String s, Principal[] principals) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
      return myAlias;
    }

    @Override
    public String @NotNull [] getServerAliases(String s, Principal[] principals) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    @Override
    @Nullable
    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
      return null;
    }

    @Override
    public X509Certificate @NotNull [] getCertificateChain(String s) {
      return myCertificates;
    }

    @Override
    @NotNull
    public PrivateKey getPrivateKey(String s) {
      return myPrivateKey;
    }
  }
}