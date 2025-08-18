// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.diagnostic.logs.LogLevelConfigurationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.nativecerts.NativeTrustedCertificates;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;

final class OsCertificatesServiceImpl implements OsCertificatesService {
  @Override
  public @NotNull Collection<X509Certificate> getCustomOsSpecificTrustedCertificates() {
    String property = System.getProperty("ide.load.os.certificates", "true");
    if (!Boolean.parseBoolean(property)) {
      LOG.info("System property 'idea.os.certificates' is set to false value ('" +
               property +
               "'), skipping loading custom certificates from operating system.");
      return Collections.emptyList();
    }

    // Ensure debug log categories are available before loading custom certificates.
    // Otherwise, CertificateManager could be requested earlier than DebugLogManager,
    // and we lose debugging info
    LogLevelConfigurationManager.getInstance();

    // see https://github.com/JetBrains/jvm-native-trusted-roots
    return NativeTrustedCertificates.getCustomOsSpecificTrustedCertificates();
  }

  private static final Logger LOG = Logger.getInstance(OsCertificatesServiceImpl.class);
}
