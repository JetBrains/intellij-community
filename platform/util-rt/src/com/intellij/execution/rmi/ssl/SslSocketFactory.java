package com.intellij.execution.rmi.ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

public class SslSocketFactory extends SSLSocketFactory {
  public static final String SSL_CA_CERT_PATH = "sslCaCertPath";
  private SSLSocketFactory myFactory;

  public SslSocketFactory() throws GeneralSecurityException, IOException {
    super();
    SSLContext ctx = SSLContext.getInstance("TLS");
    TrustManager tm;
    try {
      tm = new MyX509TrustManager();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    ctx.init(new KeyManager[]{}, new TrustManager[]{tm}, null);
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

  private static class MyX509TrustManager implements X509TrustManager {
    String serverCertFile;
    X509TrustManager trustManager;

    public MyX509TrustManager() throws Exception {
      serverCertFile = System.getProperty(SSL_CA_CERT_PATH);
      InputStream inStream = new FileInputStream(serverCertFile);

      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate ca = (X509Certificate)cf.generateCertificate(inStream);
      inStream.close();
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null);
      ks.setCertificateEntry(UUID.randomUUID().toString(), ca);
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

    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      if (trustManager == null) throw new RuntimeException("No X509TrustManager found");
      trustManager.checkServerTrusted(x509Certificates, s);
    }

    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
