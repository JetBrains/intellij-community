// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.jar.JarEntry;

final class SecureJarLoader extends JarLoader {
  private volatile @Nullable ProtectionDomain protectionDomain;
  private final Object protectionDomainMonitor = new Object();

  SecureJarLoader(@NotNull Path file, @NotNull ClassPath configuration) throws IOException {
    super(file, configuration, new JdkZipResourceFile(file, configuration.lockJars, configuration.preloadJarContents, true));
  }

  ProtectionDomain getProtectionDomain(@NotNull JarEntry entry, URL url) {
    ProtectionDomain result = protectionDomain;
    if (result != null) {
      return result;
    }

    synchronized (protectionDomainMonitor) {
      result = protectionDomain;
      if (result != null) {
        return result;
      }

      CodeSource codeSource = new CodeSource(url, entry.getCodeSigners());
      result = new ProtectionDomain(codeSource, new Permissions());
      protectionDomain = result;
    }
    return result;
  }
}