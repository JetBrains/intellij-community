/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * @author nik
 */
public class CharsetUtil {
  public static final byte[] UTF8_BOM = {0xffffffef, 0xffffffbb, 0xffffffbf};
  @NonNls public static final String UTF8 = "UTF-8";
  private static final byte FF = (byte)0xff;
  private static final byte FE = (byte)0xfe;
  private static final byte EF = (byte)0xef;
  private static final byte BB = (byte)0xbb;
  private static final byte BF = (byte)0xbf;

  public static boolean hasUTF8Bom(byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF8_BOM);
  }

  public static byte[] getUtf8Bytes(String s) {
    try {
      return s.getBytes(UTF8);
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 must be supported", e);
    }
  }

  @NotNull
  public static InputStream inputStreamSkippingBOM(@NotNull InputStream stream) throws IOException {
    assert stream.markSupported() :stream;
    stream.mark(4);
    boolean mustReset = true;
    try {
      int ret = stream.read();
      if (ret == -1) {
        return stream; // no bom
      }
      byte b0 = (byte)ret;
      if (b0 != EF && b0 != FF && b0 != FE && b0 != 0) return stream; // no bom

      ret = stream.read();
      if (ret == -1) {
        return stream; // no bom
      }
      byte b1 = (byte)ret;
      if (b0 == FF && b1 == FE) {
        stream.mark(2);
        ret = stream.read();
        if (ret == -1) {
          return stream;  // utf-16 LE
        }
        byte b2 = (byte)ret;
        if (b2 != 0) {
          return stream; // utf-16 LE
        }
        ret = stream.read();
        if (ret == -1) {
          return stream;
        }
        byte b3 = (byte)ret;
        if (b3 != 0) {
          return stream; // utf-16 LE
        }

        // utf-32 LE
        mustReset = false;
        return stream;
      }
      if (b0 == FE && b1 == FF) {
        mustReset = false;
        return stream; // utf-16 BE
      }
      if (b0 == EF && b1 == BB) {
        ret = stream.read();
        if (ret == -1) {
          return stream; // no bom
        }
        byte b2 = (byte)ret;
        if (b2 == BF) {
          mustReset = false;
          return stream; // utf-8 bom
        }

        // no bom
        return stream;
      }

      if (b0 == 0 && b1 == 0) {
        ret = stream.read();
        if (ret == -1) {
          return stream;  // no bom
        }
        byte b2 = (byte)ret;
        if (b2 != FE) {
          return stream; // no bom
        }
        ret = stream.read();
        if (ret == -1) {
          return stream;  // no bom
        }
        byte b3 = (byte)ret;
        if (b3 != FF) {
          return stream; // no bom
        }

        mustReset = false;
        return stream; // UTF-32 BE
      }

      // no bom
      return stream;
    }
    finally {
      if (mustReset) stream.reset();
    }
  }
}
