/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.apache.sanselan.util;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;

/**
 * For plugin compatibility only, DO NOT USE.
 * todo check external usages & DELETE ASAP.
 *
 * Required due to sanselan-0.98 -> commons-imaging migration.
 * e791557ca1489b02d178aa68960d645ab501e674
 */
@Deprecated
public class IOUtils {

  public static byte[] getInputStreamBytes(InputStream is) throws IOException {
    return FileUtil.loadBytes(is);
  }

  public static byte[] getFileBytes(File file) throws IOException {
    return FileUtil.loadFileBytes(file);
  }

  public static void writeToFile(byte[] src, File file) throws IOException {
    FileUtil.writeToFile(file, src);
  }

  public static void putInputStreamToFile(InputStream src, File file) throws IOException {
    FileUtil.ensureCanCreateFile(file);
    copyStreamToStream(src, new FileOutputStream(file), true);
  }

  public static void copyStreamToStream(InputStream src, OutputStream dst) throws IOException {
    copyStreamToStream(src, dst, true);
  }

  public static void copyStreamToStream(InputStream src, OutputStream dst, boolean close_streams) throws IOException {
    try {
      FileUtil.copy(new BufferedInputStream(src), new BufferedOutputStream(dst));
    }
    finally {
      if (close_streams) {
        try {
          dst.close();
        }
        catch (IOException ignore) {
        }
        try {
          dst.close();
        }
        catch (IOException ignore) {
        }
      }
    }
  }
}