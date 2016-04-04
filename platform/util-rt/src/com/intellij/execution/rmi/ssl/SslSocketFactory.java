/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.security.CompositeX509TrustManager;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

public class SslSocketFactory extends SSLSocketFactory {
  public static final String SSL_CA_CERT_PATH = "sslCaCertPath";
  public static final String SSL_CLIENT_CERT_PATH = "sslClientCertPath";
  public static final String SSL_CLIENT_KEY_PATH = "sslClientKeyPath";
  public static final String SSL_TRUST_EVERYBODY = "sslTrustEverybody";
  private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
  private SSLSocketFactory myFactory;

  public SslSocketFactory() throws GeneralSecurityException, IOException {
    super();
    SSLContext ctx = SSLContext.getInstance("TLS");
    TrustManager[] tms;
    KeyManager[] kms;
    try {
      String caCertPath = System.getProperty(SSL_CA_CERT_PATH);
      String clientCertPath = System.getProperty(SSL_CLIENT_CERT_PATH);
      String clientKeyPath = System.getProperty(SSL_CLIENT_KEY_PATH);
      boolean trustEverybody = StringUtilRt.parseBoolean(System.getProperty(SSL_TRUST_EVERYBODY), false);

      tms = trustEverybody ? new TrustManager[]{new MyTrustEverybodyManager()} :
            caCertPath == null ? new TrustManager[]{} : createTrustManagers(caCertPath);
      kms = clientCertPath != null && clientKeyPath != null
            ? new KeyManager[]{new MyKeyManager(clientCertPath, clientKeyPath)}
            : new KeyManager[]{};
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    ctx.init(kms, tms, null);
    myFactory = ctx.getSocketFactory();
  }

  @NotNull
  public static TrustManager[] createTrustManagers(@NotNull String caCertPath) throws Exception {
    String string = FileUtilRt.loadFile(new File(caCertPath));
    String[] tokens = string.split(END_CERTIFICATE);
    List<TrustManager> result = ContainerUtilRt.newArrayListWithCapacity(tokens.length);
    for (String token : tokens) {
      if (token == null || token.trim().length() == 0) continue;
      result.add(new MyTrustManager(readCertificate(stringStream(token + END_CERTIFICATE))));
    }
    return new TrustManager[]{new CompositeX509TrustManager(result.toArray(new TrustManager[result.size()]))};
  }

  @NotNull
  public static InputStream stringStream(@NotNull String str) {
    try {
      return new ByteArrayInputStream(str.getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return myFactory.createSocket(host, port);
  }

  @NotNull
  public Socket createSocket(String host, int port) throws IOException {
    return myFactory.createSocket(host, port);
  }

  @NotNull
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
    return myFactory.createSocket(host, port, localHost, localPort);
  }

  @NotNull
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return myFactory.createSocket(address, port, localAddress, localPort);
  }

  public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
    return myFactory.createSocket(socket, host, port, autoClose);
  }

  @NotNull
  public String[] getDefaultCipherSuites() {
    return myFactory.getDefaultCipherSuites();
  }

  @NotNull
  public String[] getSupportedCipherSuites() {
    return myFactory.getSupportedCipherSuites();
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
    return new PrivateKeyReader(filePath).getPrivateKey();
  }

  private static class MyTrustManager implements X509TrustManager {
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

    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      if (trustManager == null) throw new RuntimeException("No X509TrustManager found");
      trustManager.checkServerTrusted(x509Certificates, s);
    }

    @NotNull
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  private static class MyTrustEverybodyManager implements X509TrustManager {
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @NotNull
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  private static class MyKeyManager extends X509ExtendedKeyManager {
    private final String myAlias = UUID.randomUUID().toString();
    @NotNull private final X509Certificate[] myCertificates;
    @NotNull private final PrivateKey myPrivateKey;

    private MyKeyManager(@NotNull String certPath, @NotNull String keyPath) throws Exception {
      myCertificates = new X509Certificate[]{readCertificate(certPath)};
      myPrivateKey = readPrivateKey(keyPath);
    }

    @NotNull
    public String[] getClientAliases(String s, Principal[] principals) {
      return new String[]{};
    }

    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
      return myAlias;
    }

    @NotNull
    public String[] getServerAliases(String s, Principal[] principals) {
      return new String[]{};
    }

    @Nullable
    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
      return null;
    }

    @NotNull
    public X509Certificate[] getCertificateChain(String s) {
      return myCertificates;
    }

    @NotNull
    public PrivateKey getPrivateKey(String s) {
      return myPrivateKey;
    }
  }
}