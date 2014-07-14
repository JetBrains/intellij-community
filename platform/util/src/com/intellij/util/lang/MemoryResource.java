/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import sun.misc.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class MemoryResource extends Resource {
  private String myName;
  private URL myUrl;
  private final byte[] myContent;

  public MemoryResource(String name, URL url, byte[] content) {
    myName = name;
    myUrl = url;
    myContent = content;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public URL getURL() {
    return myUrl;
  }

  @Override
  public URL getCodeSourceURL() {
    return myUrl;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new UnsyncByteArrayInputStream(myContent);
  }

  @Override
  public int getContentLength() throws IOException {
    return myContent.length;
  }

  @NotNull
  public static MemoryResource load(URL baseUrl, @NotNull ZipFile zipFile, @NotNull ZipEntry entry) throws IOException {
    String name = entry.getName();
    URL url = new URL(baseUrl, name);

    byte[] content = ArrayUtil.EMPTY_BYTE_ARRAY;
    InputStream stream = zipFile.getInputStream(entry);
    if (stream != null) {
      try {
        content = FileUtil.loadBytes(stream, (int)entry.getSize());
      }
      finally {
        stream.close();
      }
    }

    return new MemoryResource(name, url, content);
  }
}
