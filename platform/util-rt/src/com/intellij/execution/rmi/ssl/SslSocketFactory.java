package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

public class SslSocketFactory extends SSLSocketFactory {
  public static final String SSL_CA_CERT_PATH = "sslCaCertPath";
  public static final String SSL_CLIENT_CERT_PATH = "sslClientCertPath";
  public static final String SSL_CLIENT_KEY_PATH = "sslClientKeyPath";
  public static final String SSL_TRUST_EVERYBODY = "sslTrustEverybody";
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
            caCertPath == null ? new TrustManager[]{} : new TrustManager[]{new MyTrustManager(caCertPath)};
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

  public Socket createSocket(InetAddress host, int port) throws IOException {
    return myFactory.createSocket(host, port);
  }

  public Socket createSocket(String host, int port) throws IOException {
    return myFactory.createSocket(host, port);
  }

  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
    return myFactory.createSocket(host, port, localHost, localPort);
  }

  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return myFactory.createSocket(address, port, localAddress, localPort);
  }

  public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
    return myFactory.createSocket(socket, host, port, autoClose);
  }

  public String[] getDefaultCipherSuites() {
    return myFactory.getDefaultCipherSuites();
  }

  public String[] getSupportedCipherSuites() {
    return myFactory.getSupportedCipherSuites();
  }

  @NotNull
  public static X509Certificate readCertificate(@NotNull String filePath) throws CertificateException, IOException {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    InputStream inStream = new FileInputStream(filePath);
    X509Certificate ca = (X509Certificate)factory.generateCertificate(inStream);
    inStream.close();
    return ca;
  }

  @NotNull
  public static PrivateKey readPrivateKey(@NotNull String filePath) throws IOException {
    return new PrivateKeyReader(filePath).getPrivateKey();
  }

  private static class MyTrustManager implements X509TrustManager {
    @NotNull private final String myCaCertPath;
    private X509TrustManager trustManager;

    private MyTrustManager(@NotNull String caCertPath) throws Exception {
      myCaCertPath = caCertPath;
      KeyStore ks = createStore();
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ks);
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
    public KeyStore createStore() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null);
      X509Certificate caCert = readCertificate(myCaCertPath);
      ks.setCertificateEntry(UUID.randomUUID().toString(), caCert);
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
    private final X509Certificate[] myCertificates;
    private final PrivateKey myPrivateKey;

    private MyKeyManager(@NotNull String certPath, @NotNull String keyPath) throws Exception {
      myCertificates = new X509Certificate[]{readCertificate(certPath)};
      myPrivateKey = readPrivateKey(keyPath);
    }

    public String[] getClientAliases(String s, Principal[] principals) {
      return new String[]{};
    }

    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
      return myAlias;
    }

    public String[] getServerAliases(String s, Principal[] principals) {
      return new String[]{};
    }

    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
      return null;
    }

    public X509Certificate[] getCertificateChain(String s) {
      return myCertificates;
    }

    public PrivateKey getPrivateKey(String s) {
      return myPrivateKey;
    }
  }
}