// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

/**
 * @author Mikhail Golubev
 */
public class CertificateTest extends LightPlatformTestCase {
  @NonNls private static final String AUTHORITY_CN = "certificates-tests.labs.intellij.net";

  @NonNls private static final String TRUSTED_CERT_CN = "trusted.certificates-tests.labs.intellij.net";
  @NonNls private static final String EXPIRED_CERT_CN = "expired.certificates-tests.labs.intellij.net";
  @NonNls private static final String SELF_SIGNED_CERT_CN = "self-signed.certificates-tests.labs.intellij.net";

  // this is the only type of certificates, which 'Common Name' field doesn't match URL of server, where it's located
  @NonNls private static final String WRONG_HOSTNAME_CERT_CN = "illegal.certificates-tests.labs.intellij.net";
  @NonNls private static final String WRONG_HOSTNAME_CERT_URL = "https://wrong-hostname.certificates-tests.labs.intellij.net";

  // TODO: Add proper tests of client authentication, when it'll be supported (see IDEA-124209).
  // By now client certificate should be specified manually via VM options like -Djavax.net.ssl.keyStore.
  @SuppressWarnings("UnusedDeclaration") @NonNls private static final String CLIENT_AUTH_CERT_CN = "client-auth.certificates-tests.labs.intellij.net";

  //private static final Logger LOG = Logger.getInstance(CertificateTest.class);

  private CloseableHttpClient myClient;
  private MutableTrustManager myTrustManager;
  private CertificateManager myCertificateManager;
  private X509Certificate myAuthorityCertificate;


  public void testSetUp() {
    assertTrue(myTrustManager.containsCertificate(AUTHORITY_CN));
  }

  /**
   * Test that expired certificate doesn't pass JSSE timestamp check and hence untrusted and added explicitly, although
   * issued by our test CA.
   */
  public void testExpiredCertificate() throws Exception {
    doTest(EXPIRED_CERT_CN, true);
  }

  /**
   * Test that self-signed certificate, that wasn't issued by out test CA, is untrusted and thus added explicitly.
   */
  public void testSelfSignedCertificate() throws Exception {
    doTest(SELF_SIGNED_CERT_CN, true);
  }

  /**
   * Hostname validity check (see {@link org.apache.http.conn.ssl.X509HostnameVerifier}) is disabled for now, so
   * it merely tests that even certificate with illegal CN field (i.e. it doesn't match requested URL).
   * is trusted, because issued by our test CA.
   */
  public void testWrongHostnameCertificate() throws Exception {
    // wrong hostname doesn't lead to any warning by now, thus it's treated the same as trusted certificate
    doTest(WRONG_HOSTNAME_CERT_URL, WRONG_HOSTNAME_CERT_CN, false);
  }

  /**
   * Test that certificate with correct hostname, validity terms and issued by our test CA is trusted.
   */
  public void testTrustedCertificate() throws Exception {
    doTest(TRUSTED_CERT_CN, false);
  }

  public void testAdditionalSystemTrustManager() throws Exception {
    Assert.assertTrue(myTrustManager.removeCertificate(myAuthorityCertificate));

    Assert.assertFalse(myTrustManager.containsCertificate(TRUSTED_CERT_CN));
    assertEquals(0, myTrustManager.getCertificates().size());
    List<X509Certificate> acceptedIssuersBefore = Arrays.asList(myCertificateManager.getTrustManager().getAcceptedIssuers());
    Assert.assertFalse(acceptedIssuersBefore.contains(myAuthorityCertificate));

    X509TrustManager customTrustManager = ConfirmingTrustManager.createTrustManagerFromCertificates(
      List.of(myAuthorityCertificate)
    );

    myCertificateManager.getTrustManager().addSystemTrustManager(customTrustManager);
    try {
      List<X509Certificate> acceptedIssuersAfter = Arrays.asList(myCertificateManager.getTrustManager().getAcceptedIssuers());
      Assert.assertTrue(acceptedIssuersAfter.contains(myAuthorityCertificate));

      doTestHttpCall("https://" + TRUSTED_CERT_CN);

      // No certificates were added to mutable store
      assertEquals(0, myTrustManager.getCertificates().size());
      Assert.assertFalse(myTrustManager.containsCertificate(TRUSTED_CERT_CN));
    }
    finally {
      myCertificateManager.getTrustManager().removeSystemTrustManager(customTrustManager);
    }
  }

  private void doTest(@NonNls String alias, boolean willBeAdded) throws Exception {
    doTest("https://" + alias, alias, willBeAdded);
  }

  private void doTest(@NotNull String url, @NotNull String alias, boolean added) throws Exception {
    doTestHttpCall(url);

    if (added) {
      assertTrue(myTrustManager.containsCertificate(alias));
      assertEquals(2, myTrustManager.getCertificates().size());
    }
    else {
      // only CA certificate
      assertEquals(1, myTrustManager.getCertificates().size());
    }
  }

  private void doTestHttpCall(@NotNull String url) throws Exception {
    try (CloseableHttpResponse response = myClient.execute(new HttpGet(url))) {
      assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
    }
  }

  public void testDeadlockDetection() throws Exception {
    final Ref<Throwable> throwableRef = new Ref<>();

    final long interruptionTimeout = CertificateManager.DIALOG_VISIBILITY_TIMEOUT + 1000;
    // Will be interrupted after at most interruptionTimeout (6 seconds originally)
    Thread[] t = {null};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final Thread thread = new Thread(() -> {
        try {
          boolean accepted = CertificateManager.Companion.showAcceptDialog(() -> {
            // this dialog will be attempted to show only if blocking thread was forcibly interrupted after timeout
            throw new AssertionError("Deadlock was not detected in time");
          });
          // should be rejected after 5 seconds
          assertFalse("Certificate should be rejected", accepted);
        }
        catch (Throwable e) {
          throwableRef.set(e);
        }
      }, "Test EDT-blocking thread");
      thread.start();
      try {
        thread.join(interruptionTimeout);
      }
      catch (InterruptedException ignored) {
        // No one will attempt to interrupt EDT, right?
      }
      finally {
        if (thread.isAlive()) {
          thread.interrupt();
          fail("Deadlock was not detected in time");
        }
      }
      t[0] = thread;
    }, ModalityState.any());
    if (!throwableRef.isNull()) {
      throw new AssertionError(throwableRef.get());
    }
    t[0].join();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCertificateManager = CertificateManager.getInstance();
    // add CA certificate
    myTrustManager = myCertificateManager.getCustomTrustManager();
    myAuthorityCertificate = CertificateUtil.loadX509Certificate(getTestDataPath() + "certificates/ca.crt");

    myClient = HttpClientBuilder.create()
      .setSslcontext(myCertificateManager.getSslContext())
      .setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
      .build();

    myTrustManager.addCertificate(myAuthorityCertificate);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      assertTrue(myTrustManager.removeAllCertificates());
      assertEmpty(myTrustManager.getCertificates());
    }
    finally {
      try {
        myClient.close();
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        super.tearDown();
      }
    }
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }
}
