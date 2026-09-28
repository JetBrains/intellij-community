// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.net.ssl.ConfirmingTrustManager.CertificateConfirmationParameters
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * Checks that [ConfirmingTrustManager] rejects an untrusted certificate in a headless run and does not save it.
 * A headless run cannot ask the user to confirm the certificate.
 */
@TestApplication
internal class ConfirmingTrustManagerHeadlessTest {
  @BeforeEach
  fun checkHeadlessWithoutAutomaticAcceptance() {
    assertThat(ApplicationManager.getApplication().isHeadlessEnvironment).isTrue()
    assertThat(CertificateManager.getInstance().state.ACCEPT_AUTOMATICALLY).isFalse()
  }

  @Test fun rejectsUntrustedCertificate(@TempDir tempDir: Path) {
    val storePath = tempDir.resolve("cacerts")
    val trustManager = ConfirmingTrustManager.createForStorage(storePath.toString(), CertificateManager.DEFAULT_PASSWORD)

    assertThatThrownBy { trustManager.checkServerTrusted(arrayOf(untrustedCertificate()), "RSA") }
      .isInstanceOf(CertificateException::class.java)

    assertThat(trustManager.customManager.certificates).isEmpty()
    assertThat(storePath).doesNotExist()
  }

  @Test fun rejectsUntrustedCertificateWhenCallerAsksForConfirmation(@TempDir tempDir: Path) {
    val storePath = tempDir.resolve("cacerts")
    val trustManager = ConfirmingTrustManager.createForStorage(storePath.toString(), CertificateManager.DEFAULT_PASSWORD)
    val parameters = CertificateConfirmationParameters.askConfirmation(true, null, null)

    assertThatThrownBy { trustManager.checkServerTrusted(arrayOf(untrustedCertificate()), "RSA", parameters) }
      .isInstanceOf(CertificateException::class.java)

    assertThat(trustManager.customManager.certificates).isEmpty()
    assertThat(storePath).doesNotExist()
  }
}

/** The test certificate authority is self-signed and expired, so no system trust store accepts it. */
private fun untrustedCertificate(): X509Certificate =
  checkNotNull(CertificateUtil.loadX509Certificate(PlatformTestUtil.getPlatformTestDataPath() + "certificates/ca.crt"))
