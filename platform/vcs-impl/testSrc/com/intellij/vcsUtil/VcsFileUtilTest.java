// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

public class VcsFileUtilTest {

  @Test
  public void unescape_cyrillic() {
    String charset = Charset.defaultCharset().name();
    String charsetMessage = " Default charset: " + charset;
    assertEquals("Cyrillic folder was unescaped incorrectly." + charsetMessage,
                 "папка/file.txt",
                 VcsFileUtil.unescapeGitPath("\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/file.txt", charset));
    assertEquals("Cyrillic folder with file name were unescaped incorrectly." + charsetMessage,
                 "папка/документ",
                 VcsFileUtil.unescapeGitPath(
                   "\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/\\320\\264\\320\\276\\320\\272\\321\\203\\320\\274\\320\\265\\320\\275\\321\\202",
                   charset));
  }
}