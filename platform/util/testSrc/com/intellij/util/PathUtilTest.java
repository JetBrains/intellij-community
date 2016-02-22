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

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.PathUtilRt.Platform;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathUtilTest {
  @Test
  public void fileNameValidityBasics() {
    assertFalse(PathUtilRt.isValidFileName("", false));
    assertFalse(PathUtilRt.isValidFileName(".", false));
    assertFalse(PathUtilRt.isValidFileName("..", false));
    assertFalse(PathUtilRt.isValidFileName("a/b", false));
    assertFalse(PathUtilRt.isValidFileName("a\\b", false));
  }

  @Test
  public void fileNameValidityPlatform() {
    assertFalse(PathUtilRt.isValidFileName("a:b", true));
    assertTrue(PathUtilRt.isValidFileName("a:b", Platform.UNIX, false, null));
    assertFalse(PathUtilRt.isValidFileName("a:b", Platform.WINDOWS, false, null));
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void fileNameValidityCharset() {
    Charset cp1251 = Charset.forName("Cp1251");
    assertTrue(PathUtilRt.isValidFileName("имя файла", Platform.UNIX, false, cp1251));
    assertFalse(PathUtilRt.isValidFileName("název souboru", Platform.UNIX, false, cp1251));

    Charset cp1252 = Charset.forName("Cp1252");
    assertFalse(PathUtilRt.isValidFileName("имя файла", Platform.UNIX, false, cp1252));
    assertTrue(PathUtilRt.isValidFileName("název souboru", Platform.UNIX, false, cp1252));

    assertTrue(PathUtilRt.isValidFileName("имя файла", Platform.UNIX, false, CharsetToolkit.UTF8_CHARSET));
    assertTrue(PathUtilRt.isValidFileName("název souboru", Platform.UNIX, false, CharsetToolkit.UTF8_CHARSET));
    assertTrue(PathUtilRt.isValidFileName("文件名", Platform.UNIX, false, CharsetToolkit.UTF8_CHARSET));
  }
}