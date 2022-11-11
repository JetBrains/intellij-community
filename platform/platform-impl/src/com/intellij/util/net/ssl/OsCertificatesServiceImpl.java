// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.nativecerts.NativeTrustedCertificates;

import java.security.cert.X509Certificate;
import java.util.Collection;

final class OsCertificatesServiceImpl implements OsCertificatesService {
  @Override
  public @NotNull Collection<X509Certificate> getCustomOsSpecificTrustedCertificates() {
    // see https://github.com/JetBrains/jvm-native-trusted-roots
    return NativeTrustedCertificates.getCustomOsSpecificTrustedCertificates();
  }
}
