/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("SpellCheckingInspection")
public class Base64Test {
  private static final Charset UTF8 = Charset.forName("UTF-8");

  @Test
  public void decodePadded() {
    decode("YW55IGNhcm5hbCBwbGVhcw==", "any carnal pleas");
    decode("YW55IGNhcm5hbCBwbGVhc3U=", "any carnal pleasu");
    decode("YW55IGNhcm5hbCBwbGVhc3Vy", "any carnal pleasur");
  }

  @Test
  public void decodeUnpadded() {
    decode("YW55IGNhcm5hbCBwbGVhcw", "any carnal pleas");
    decode("YW55IGNhcm5hbCBwbGVhc3U", "any carnal pleasu");
    decode("YW55IGNhcm5hbCBwbGVhc3Vy", "any carnal pleasur");
  }

  private static void decode(String base64, String expected) {
    assertEquals(expected, new String(Base64.decode(base64), UTF8));
  }
}