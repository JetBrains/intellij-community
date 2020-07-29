// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.apache.sanselan.util;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;

/**
 * todo check external usages & DELETE ASAP.
 * <p>
 * Required due to sanselan-0.98 -> commons-imaging migration.
 * e791557ca1489b02d178aa68960d645ab501e674
 *
 * @deprecated For plugin compatibility only, DO NOT USE.
 */
@Deprecated
public final class IOUtils {

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
    doCopyStreamToStream(src, new FileOutputStream(file));
  }

  public static void copyStreamToStream(InputStream src, OutputStream dst) throws IOException {
    doCopyStreamToStream(src, dst);
  }

  private static void doCopyStreamToStream(InputStream src, OutputStream dst) throws IOException {
    try (
      BufferedInputStream bis = new BufferedInputStream(src);
      BufferedOutputStream bos = new BufferedOutputStream(dst)) {
      FileUtil.copy(bis, bos);
      bos.flush();
    }
  }
}