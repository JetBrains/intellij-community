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

import java.io.UnsupportedEncodingException;

/**
 * @author nik
 */
public class CharsetUtil {
  public static final byte[] UTF8_BOM = {0xffffffef, 0xffffffbb, 0xffffffbf};
  @NonNls public static final String UTF8 = "UTF-8";

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
}
