// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public abstract class SslEntityReader {
  private static SslEntityReader ourInstance;

  @NotNull
  public static SslEntityReader getInstance() {
    SslEntityReader res = ourInstance;
    if (res == null) {
      res = new SslEntityReaderImpl();
      ourInstance = res;
    }
    return res;
  }

  @NotNull
  public abstract List<? extends Entity> read(@NotNull InputStream stream) throws IOException;

  public interface Entity {}
  public interface PrivateKeyEntity extends Entity {}
  public interface CertificateEntity extends Entity {
    X509Certificate get() throws IOException;
  }
  public interface EncryptedPrivateKeyEntity extends PrivateKeyEntity {
    PrivateKey get(char[] password) throws IOException;
  }
  public interface UnencryptedPrivateKeyEntity extends PrivateKeyEntity {
    PrivateKey get() throws IOException;
  }
}