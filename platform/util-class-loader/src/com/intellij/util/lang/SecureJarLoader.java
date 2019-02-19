// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

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

class SecureJarLoader extends JarLoader {
  @Nullable private volatile ProtectionDomain myProtectionDomain;

  SecureJarLoader(URL url, int index, ClassPath configuration) throws IOException {
    super(url, index, configuration);
  }

  @Override
  protected Resource instantiateResource(URL url, ZipEntry entry) throws IOException {
    return new MySecureResource(url, (JarEntry)entry);
  }

  @NotNull
  @Override
  protected ZipFile createZipFile(String path) throws IOException {
    return new JarFile(path);
  }

  private class MySecureResource extends JarLoader.MyResource {
    MySecureResource(URL url, JarEntry entry) throws IOException {
      super(url, entry);
    }

    @Override
    public byte[] getBytes() throws IOException {
      JarFile file = (JarFile)getZipFile();
      InputStream stream = null;
      byte[] result;
      try {
        stream = file.getInputStream(myEntry);
        result = FileUtilRt.loadBytes(stream, (int)myEntry.getSize());
        if (myProtectionDomain == null) {
          JarEntry jarEntry = file.getJarEntry(myEntry.getName());
          CodeSource codeSource = new CodeSource(myUrl, jarEntry.getCodeSigners());
          myProtectionDomain = new ProtectionDomain(codeSource, new Permissions());
        }
      } finally {
        if (stream != null) stream.close();
        releaseZipFile(file);
      }
      return result;
    }

    @Nullable
    @Override
    public ProtectionDomain getProtectionDomain() {
      return myProtectionDomain;
    }
  }
}
