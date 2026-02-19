// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.newvfs.persistent.dev.OptimizedCaseInsensitiveStringHashing;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CaseInsensitiveStringHashTest {

  public static final int TEST_STRINGS_COUNT = 1024;

  private final String[] testStrings = new String[TEST_STRINGS_COUNT];

  @Before
  public void setUp() throws Exception {
    for (int i = 0; i < testStrings.length; i++) {
      testStrings[i] = generateRandomString(64);
    }
  }

  @Test
  public void optimizedCaseInsensitiveHashCode_isSameForChar_AndForLowerCasedChar_ASCII() {
    for (int i = 0; i < 0x7F; i++) {
      char ch = (char)i;
      int caseInsensitiveHash = OptimizedCaseInsensitiveStringHashing.caseInsensitiveHashCode((byte)ch);
      char lch = StringUtilRt.toLowerCase(ch);
      int caseInsensitiveHashFromLowerCasedChar = OptimizedCaseInsensitiveStringHashing.caseInsensitiveHashCode((byte)lch);
      assertEquals(
        "'" + ch + "'.caseInsensitiveHash(=" + caseInsensitiveHash + ")" +
        " <> " +
        "toLowerCase('" + ch + "')[=" + lch + "].caseInsensitiveHash(=" + caseInsensitiveHashFromLowerCasedChar + ")",
        caseInsensitiveHash,
        caseInsensitiveHashFromLowerCasedChar
      );
    }
  }

  @Test
  public void caseInsensitiveHashCode_IsSameForAString_AndForLowerCasedString() {
    for (String string : testStrings) {
      int caseInsensitiveHash = StringUtilRt.stringHashCodeInsensitive(string);

      int caseInsensitiveHashOfLowerCased = StringUtilRt.stringHashCodeInsensitive(lowerCase(string));
      if (!(caseInsensitiveHash == caseInsensitiveHashOfLowerCased)) {
        fail("[" + string + "]: " +
             "hash1(=" + caseInsensitiveHash + "), " +
             "hash2(=" + caseInsensitiveHashOfLowerCased + ") -- must be all equal!"
        );
      }
    }
  }

  @Test
  public void optimizedCaseInsensitiveHashCode_IsSameForAString_AndForLowerCasedString() {
    Hash.Strategy<String> hashingStrategy = OptimizedCaseInsensitiveStringHashing.instance();
    for (String string : testStrings) {
      int caseInsensitiveHash = hashingStrategy.hashCode(string);

      int caseInsensitiveHashOfLowerCased = hashingStrategy.hashCode(lowerCase(string));
      if (!(caseInsensitiveHash == caseInsensitiveHashOfLowerCased)) {
        fail("[" + string + "]: " +
             "hash1(=" + caseInsensitiveHash + "), " +
             "hash2(=" + caseInsensitiveHashOfLowerCased + ") -- must be all equal!"
        );
      }
    }
  }


  private static @NotNull String lowerCase(String string) {
    //String.toLowerCase() is not the same as char-by-char toLowerCase, because of surrogate symbols, what are lower-cased
    //  differently together, and in separation. To make it consistent, we convert String to lower-case char-by-char:
    int length = string.length();
    char[] lowerCased = new char[length];
    for (int i = 0; i < length; i++) {
      char ch = string.charAt(i);
      char lch = StringUtilRt.toLowerCase(ch);
      lowerCased[i] = lch;
    }
    return new String(lowerCased);
  }

  private static String generateRandomString(int size) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    char[] chars = new char[size];
    for (int i = 0; i < size; i++) {
      chars[i] = (char)rnd.nextInt(0, Character.MAX_VALUE + 1);
    }
    return new String(chars);
  }
}