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
package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtilRt;
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
 * @since 12/07/2011
 */
public class JarMemoryLoader {
  public static final String SIZE_ENTRY = "META-INF/jb/$$size$$";

  private final Map<String, Resource> myResources = Collections.synchronizedMap(new HashMap<String, Resource>()); // todo do we need it ?

  private JarMemoryLoader() { }

  public Resource getResource(String entryName) {
    return myResources.remove(entryName);
  }

  @Nullable
  public static JarMemoryLoader load(ZipFile zipFile, URL baseUrl, Map<Resource.Attribute, String> attributes) throws IOException {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    if (!entries.hasMoreElements()) return null;

    ZipEntry sizeEntry = entries.nextElement();
    if (sizeEntry == null || !sizeEntry.getName().equals(SIZE_ENTRY)) return null;

    byte[] bytes = FileUtilRt.loadBytes(zipFile.getInputStream(sizeEntry), 2);
    int size = ((bytes[1] & 0xFF) << 8) + (bytes[0] & 0xFF);

    JarMemoryLoader loader = new JarMemoryLoader();
    for (int i = 0; i < size && entries.hasMoreElements(); i++) {
      ZipEntry entry = entries.nextElement();
      MemoryResource resource = MemoryResource.load(baseUrl, zipFile, entry, attributes);
      loader.myResources.put(entry.getName(), resource);
    }
    return loader;
  }
}
