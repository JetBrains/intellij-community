package com.intellij.util.net.ssl;

import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;


/**
 * @author Mikhail Golubev
 */
public class CertificateTest extends PlatformTestCase {
  @NonNls private static final String AUTHORITY_CN = "certificates-tests.labs.intellij.net";

  @NonNls private static final String TRUSTED_CERT_CN = "trusted.certificates-tests.labs.intellij.net";
  @NonNls private static final String EXPIRED_CERT_CN = "expired.certificates-tests.labs.intellij.net";
  @NonNls private static final String SELF_SIGNED_CERT_CN = "self-signed.certificates-tests.labs.intellij.net";

  // this is the only type of certificates, which 'Common Name' field doesn't match URL of server, where it's located
  @NonNls private static final String WRONG_HOSTNAME_CERT_CN = "illegal.certificates-tests.labs.intellij.net";
  @NonNls private static final String WRONG_HOSTNAME_CERT_URL = "https://wrong-hostname.certificates-tests.labs.intellij.net";

  private CloseableHttpClient myClient;
  private ConfirmingTrustManager.MutableTrustManager myTrustManager;


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


  private void doTest(@NonNls String alias, boolean willBeAdded) throws Exception {
    doTest("https://" + alias, alias, willBeAdded);
  }

  private void doTest(@NotNull String url, @NotNull String alias, boolean added) throws Exception {
    CloseableHttpResponse response = myClient.execute(new HttpGet(url));
    try {
      assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
    }
    finally {
      response.close();
    }
    if (added) {
      assertTrue(myTrustManager.containsCertificate(alias));
      assertEquals(2, myTrustManager.getCertificates().size());
    }
    else {
      // only CA certificate
      assertEquals(1, myTrustManager.getCertificates().size());
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CertificatesManager certificatesManager = CertificatesManager.getInstance();
    myClient = HttpClientBuilder.create()
      .setSslcontext(certificatesManager.getSslContext())
      .setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
      .build();

    // add CA certificate
    myTrustManager = certificatesManager.getCustomTrustManager();
    assertTrue(myTrustManager.addCertificate(getTestDataPath() + "certificates/ca.crt"));
    assertTrue(myTrustManager.containsCertificate(AUTHORITY_CN));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      assertTrue(myTrustManager.removeAllCertificates());
      assertEmpty(myTrustManager.getCertificates());
    }
    finally {
      myClient.close();
    }
    super.tearDown();
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/";
  }
}
