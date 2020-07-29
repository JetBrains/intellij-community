// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Dmitry Avdeev
 */
public final class JarMemoryLoader {
  /** Special entry to keep the number of reordered classes in jar. */
  public static final String SIZE_ENTRY = "META-INF/jb/$$size$$";

  private final Map<String, Resource> myResources = Collections.synchronizedMap(new HashMap<String, Resource>()); // todo do we need it ?

  private JarMemoryLoader() { }

  public Resource getResource(@NotNull String entryName) {
    return myResources.remove(entryName);
  }

  @Nullable static JarMemoryLoader load(@NotNull ZipFile zipFile, @NotNull URL baseUrl, @Nullable JarLoader attributesProvider) throws IOException {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    if (!entries.hasMoreElements()) {
      return null;
    }

    ZipEntry sizeEntry = entries.nextElement();
    if (sizeEntry == null || !sizeEntry.getName().equals(SIZE_ENTRY)) {
      return null;
    }

    byte[] bytes = FileUtilRt.loadBytes(zipFile.getInputStream(sizeEntry), 2);
    int size = ((bytes[1] & 0xFF) << 8) + (bytes[0] & 0xFF);

    JarMemoryLoader loader = new JarMemoryLoader();
    for (int i = 0; i < size && entries.hasMoreElements(); i++) {
      ZipEntry entry = entries.nextElement();
      MemoryResource resource = MemoryResource.load(baseUrl, zipFile, entry, attributesProvider != null ? attributesProvider.getAttributes() : null);
      loader.myResources.put(entry.getName(), resource);
    }
    return loader;
  }
}
