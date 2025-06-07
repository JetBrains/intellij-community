// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.net.ssl

import com.intellij.util.net.ssl.CertificateProvider
import com.intellij.util.net.ssl.CertificateWarningDialogProvider
import com.intellij.util.net.ssl.ConfirmingTrustManager
import java.security.cert.X509Certificate

private class CertificateWarningDialogProviderImpl : CertificateWarningDialogProvider {

  override fun createCertificateWarningDialog(
    certificates: List<X509Certificate>,
    manager: ConfirmingTrustManager.MutableTrustManager,
    remoteHost: String?, authType: String, certificateProvider: CertificateProvider,
  ): CertificateWarningDialog {
    return CertificateWarningDialog(certificates, remoteHost, manager, authType, certificateProvider)
  }
}