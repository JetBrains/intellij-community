// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.diagnostic.logs.LogLevelConfigurationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.nativecerts.NativeTrustedCertificates;

import java.security.cert.X509Certificate;
import java.util.Collection;

final class OsCertificatesServiceImpl implements OsCertificatesService {
  @Override
  public @NotNull Collection<X509Certificate> getCustomOsSpecificTrustedCertificates() {
    // Ensure debug log categories are available before loading custom certificates.
    // Otherwise, CertificateManager could be requested earlier than DebugLogManager,
    // and we lose debugging info
    LogLevelConfigurationManager.getInstance();

    // see https://github.com/JetBrains/jvm-native-trusted-roots
    return NativeTrustedCertificates.getCustomOsSpecificTrustedCertificates();
  }
}
