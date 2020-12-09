// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

final class SecureJarLoader extends JarLoader {
  private @Nullable ProtectionDomain myProtectionDomain;
  private final Object myProtectionDomainMonitor = new Object();

  SecureJarLoader(@NotNull Path file, @NotNull ClassPath configuration) throws IOException {
    super(file, configuration, new JdkZipFile(file, configuration.lockJars, true));
  }

  @Override
  protected @NotNull Resource instantiateResource(@NotNull ZipEntry entry) throws IOException {
    return new SecureJarResource(url, (JarEntry)entry);
  }

  private final class SecureJarResource extends ZipFileResource {
    SecureJarResource(@NotNull URL baseUrl, @NotNull JarEntry entry) {
      super(baseUrl, entry);
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
      JarFile file = (JarFile)((JdkZipFile)zipFile).getZipFile();
      try {
        InputStream stream = file.getInputStream(entry);
        try {
          byte[] result = Resource.loadBytes(stream, (int)entry.getSize());
          synchronized (myProtectionDomainMonitor) {
            if (myProtectionDomain == null) {
              JarEntry jarEntry = file.getJarEntry(entry.getName());
              CodeSource codeSource = new CodeSource(getURL(), jarEntry.getCodeSigners());
              myProtectionDomain = new ProtectionDomain(codeSource, new Permissions());
            }
          }
          return result;
        }
        finally {
          stream.close();
        }
      }
      finally {
        if (!configuration.lockJars) {
          file.close();
        }
      }
    }

    @Override
    public @Nullable ProtectionDomain getProtectionDomain() {
      synchronized (myProtectionDomainMonitor) {
        return myProtectionDomain;
      }
    }
  }
}