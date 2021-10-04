// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang.java6;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class SecureJarLoader extends JarLoader {
  private @Nullable ProtectionDomain myProtectionDomain;
  private final Object myProtectionDomainMonitor = new Object();

  SecureJarLoader(@NotNull URL url, @NotNull String filePath, @NotNull ClassPath configuration) throws IOException {
    super(url, filePath, configuration);
  }

  @NotNull
  @Override
  protected Resource instantiateResource(@NotNull URL url, @NotNull ZipEntry entry) throws IOException {
    return new MySecureResource(url, (JarEntry)entry);
  }

  @NotNull
  @Override
  protected ZipFile createZipFile(@NotNull String path) throws IOException {
    return new JarFile(path);
  }

  private final class MySecureResource extends JarLoader.MyResource {
    MySecureResource(@NotNull URL url, @NotNull JarEntry entry) throws IOException {
      super(url, entry);
    }

    @NotNull
    @Override
    public byte[] getBytes() throws IOException {
      JarFile file = (JarFile)getZipFile();
      try {
        InputStream stream = file.getInputStream(myEntry);
        try {
          byte[] result = FileUtilRt.loadBytes(stream, (int)myEntry.getSize());
          synchronized (myProtectionDomainMonitor) {
            if (myProtectionDomain == null) {
              JarEntry jarEntry = file.getJarEntry(myEntry.getName());
              CodeSource codeSource = new CodeSource(myUrl, jarEntry.getCodeSigners());
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
        releaseZipFile(file);
      }
    }

    @Nullable
    @Override
    public ProtectionDomain getProtectionDomain() {
      synchronized (myProtectionDomainMonitor) {
        return myProtectionDomain;
      }
    }
  }
}