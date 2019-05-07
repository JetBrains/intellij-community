/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.security;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public class CompositeX509TrustManager implements X509TrustManager {
  private final List<X509TrustManager> myManagers = ContainerUtilRt.newArrayList();

  public CompositeX509TrustManager(@NotNull TrustManager[]... managerSets) {
    for (TrustManager[] set : managerSets) {
      for (TrustManager manager : set) {
        if (manager instanceof X509TrustManager) {
          myManagers.add((X509TrustManager)manager);
        }
      }
    }
  }

  public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
    throw new UnsupportedOperationException();
  }

  public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
    for (X509TrustManager manager : myManagers) {
      try {
        manager.checkServerTrusted(certificates, s);
        return;
      }
      catch (CertificateException ignored) { }
    }
    throw new CertificateException("No trusting managers found for " + s);
  }

  @NotNull
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}