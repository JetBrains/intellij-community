// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.net.ssl.CertificateUtil;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLoadHandler;
import org.cef.security.CefSSLInfo;
import org.cef.security.CefX509Certificate;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.ui.jcef.JBCefTestHelper.await;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JBCefSSLCertificateTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  final static String CER_BASE64 =
    "MIIDijCCAnKgAwIBAgIJANeDdOXTlBwfMA0GCSqGSIb3DQEBCwUAMHMxCzAJBgNVBAYTAkRFMQ8w" +
    "DQYDVQQIEwZCZXJsaW4xDzANBgNVBAcTBkJlcmxpbjESMBAGA1UEChMJSmV0QnJhaW5zMRAwDgYD" +
    "VQQLEwdSdW50aW1lMRwwGgYDVQQDExNWbGFkaW1pciBLaGFyaXRvbm92MB4XDTIyMTAxNzA5MzAx" +
    "NFoXDTQyMDcwNDA5MzAxNFowczELMAkGA1UEBhMCREUxDzANBgNVBAgTBkJlcmxpbjEPMA0GA1UE" +
    "BxMGQmVybGluMRIwEAYDVQQKEwlKZXRCcmFpbnMxEDAOBgNVBAsTB1J1bnRpbWUxHDAaBgNVBAMT" +
    "E1ZsYWRpbWlyIEtoYXJpdG9ub3YwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDBNDzY" +
    "IncY2OpfpDHMFV1mcTyy14rBCLIV9DVYQmIIGWNIOVIvC42PROX/55cXAsbxTMci9QUq6P0QRvBE" +
    "dOpIQb8VviR35JEo3e1+vg3a8aggWzSIvbG11NqRN7p/8IZ6ANGgJA+KlblssLjuk+e08RMm/0FL" +
    "4HHYCHx5TkrAlGX5IhIfATp5EZEwboRksAJudrGpjlMwjibMycNL3zFD4qM751N2J9a1r+cfykUY" +
    "Ahpl/+o1+K0OhIUCim+qs4xm6yPlbkGaE8TJJB7QA2lsd8nC0LcrMe7ORRF3L0nAg75rWbsVNQME" +
    "iI0tZZbhm16gUZfwfXxto8YX+QoJYCshAgMBAAGjITAfMB0GA1UdDgQWBBT0UpMP8dBViYg+MJUA" +
    "Iac6rqfAAzANBgkqhkiG9w0BAQsFAAOCAQEATswa9pPVLEww+oXcwlCCEYL4hAqaZji5Lnto6Rrc" +
    "KXqQ++VHN7xMdaJPTx2edtE3Zl4Krkw0RIkDx4cRVY4+CrbdlYEfJlxzxyKmB6dqRqtM7n3oO+NT" +
    "DL1p4JspkwMHsJHTfugbpxd/Dm+6mi+rt9lzY67idjk0AZCzwJbDPxcjC84bDrdELyr3SFsIEczx" +
    "ABWSCtQXGQx2mXIBLHyEjoPAMFzZcPVK63nJz17rkBDp5k9XaG+iEi5Q36wwDgQyoYoOCrR+Wcxx" +
    "WEHAvWuOha1AywLjaPowUTexRMZTUpTJZ/fei+6CdTnwGYtq6gn04pPSw6pQ9RCtrLUYkjQIzw==";
  private CefSSLInfo mySSLInfo = null;
  private JBCefBrowser myBrowser = null;

  @Before
  public void before() throws CertificateException {
    mySSLInfo = makeSSLInfo();
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();

    // Clean up the certificate storage if needed
    var trustManager = CertificateManager.getInstance().getTrustManager().getCustomManager();
    if (mySSLInfo != null && mySSLInfo.certificate != null) {
      for (X509Certificate certificate : mySSLInfo.certificate.getCertificatesChain()) {
        if (trustManager.containsCertificate(CertificateUtil.getCommonName(certificate))) {
          trustManager.removeCertificate(certificate);
        }
      }
    }
  }

  static class CertificateErrorCallback implements CefCallback {
    CountDownLatch myLatch = new CountDownLatch(1);
    AtomicBoolean myContinueCalled = new AtomicBoolean(false);
    AtomicBoolean myCancelCalled = new AtomicBoolean(false);

    @Override
    public void Continue() {
      myContinueCalled.set(true);
      myLatch.countDown();
    }

    @Override
    public void cancel() {
      myCancelCalled.set(true);
      myLatch.countDown();
    }

    public void waitCall() {
      await(myLatch);
    }

    public boolean continueCalled() {
      return myContinueCalled.get();
    }

    public boolean cancelCalled() {
      return myCancelCalled.get();
    }
  }

  @Test
  public void test() {
    // start the browser
    JBCefClient client = JBCefApp.getInstance().createClient();
    myBrowser = JBCefBrowser.createBuilder()
      .setClient(client)
      .setCreateImmediately(true)
      .build();

    invokeAndWaitForLoad(myBrowser, () -> myBrowser.loadHTML("chrome://version/"));

    // Call CertificateErrorCallback with an unknown(for the custom trust manager) certificate
    {
      var callback = new CertificateErrorCallback();
      boolean exit_code = client.getCefClient().onCertificateError(myBrowser.getCefBrowser(),
                                                                   CefLoadHandler.ErrorCode.ERR_CERT_AUTHORITY_INVALID,
                                                                   "some_url",
                                                                   mySSLInfo,
                                                                   callback
      );
      callback.waitCall();
      assertTrue(exit_code);
      assertTrue(callback.cancelCalled());
      assertFalse(callback.continueCalled());
    }

    for (var certificate : mySSLInfo.certificate.getCertificatesChain()) {
      CertificateManager.getInstance().getTrustManager().getCustomManager().addCertificate(certificate);
    }

    // Call CertificateErrorCallback with a known(for the custom trust manager) certificate
    {
      var callback = new CertificateErrorCallback();
      boolean exit_code = client.getCefClient().onCertificateError(myBrowser.getCefBrowser(),
                                                                   CefLoadHandler.ErrorCode.ERR_CERT_AUTHORITY_INVALID,
                                                                   "some_url",
                                                                   mySSLInfo,
                                                                   callback
      );
      callback.waitCall();
      assertTrue(exit_code);
      assertTrue(callback.continueCalled());
      assertFalse(callback.cancelCalled());
    }

    for (var certificate : mySSLInfo.certificate.getCertificatesChain()) {
      CertificateManager.getInstance().getTrustManager().getCustomManager().removeCertificate(certificate);
    }

    // Remove the certificate from the custom trust manger
    {
      var callback = new CertificateErrorCallback();
      boolean exit_code = client.getCefClient().onCertificateError(myBrowser.getCefBrowser(),
                                                                   CefLoadHandler.ErrorCode.ERR_CERT_AUTHORITY_INVALID,
                                                                   "some_url",
                                                                   mySSLInfo,
                                                                   callback
      );
      callback.waitCall();
      assertTrue(exit_code);
      assertFalse(callback.continueCalled());
      assertTrue(callback.cancelCalled());
    }
  }

  static private CefSSLInfo makeSSLInfo() {
    return new CefSSLInfo(4 /*CERT_STATUS_AUTHORITY_INVALID*/,
                          new CefX509Certificate(new byte[][]{Base64.getDecoder().decode(CER_BASE64)}));
  }
}
