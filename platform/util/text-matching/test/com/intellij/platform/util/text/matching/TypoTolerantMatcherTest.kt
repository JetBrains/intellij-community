// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching;

import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.TypoTolerantMatcher;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TypoTolerantMatcherTest {
  @Test
  public void testStability() {
    SplittableRandom random = new SplittableRandom(1);
    String[] data =
      Stream.generate(() -> {
        StringBuilder sb = new StringBuilder();
        random.ints(random.nextInt(5, 25), 'a', 'z')
          .map(ch ->
                 random.nextInt(100) == 0 ? "0123456789$_@%вгдежзиклмно".charAt(ch - 'a') :
                 random.nextInt(6) == 0 ? Character.toUpperCase(ch) : ch)
          .forEach(sb::appendCodePoint);
        return sb.toString();
      }).limit(20000).toArray(String[]::new);
    TypoTolerantMatcher matcher = new TypoTolerantMatcher("asd", NameUtil.MatchingCaseSensitivity.FIRST_LETTER, "");
    List<String> matched = Arrays.stream(data).filter(matcher::matches).toList();
    List<String> expected =
      List.of("aqvQpSfmT", "arvIeSdS", "agofjgjwovuUiSmdalto", "jasridvantDr", "asvioqecqrkujxuLoiDo", "ssHDdcogvKq", "asQDqhkej",
              "gastDtHdqgG", "ahSDaks", "abKluwAdwxJoUyibvgeoh", "aDbsnmlGlJuBJsDi", "aamaoRrghlcD", "aadnjwforytcqwa", "adovoaqvSximVAdD",
              "adyqfdmDaryuakWicnjcj", "ahNbcEcpIsD", "aLompgtlMkDdypdxwmvvUG", "ajxoADelNmdbmvutbidrde", "nasbcDhbfXx", "aDhbEtoublcDryyh",
              "pAeloeorWqXslbSDbv", "asnnWshffqbujmcOd", "adXovaMuDYjyrlexj");
    assertEquals(expected, matched);
  }

  @Test
  public void testEmptyPattern() {
    TypoTolerantMatcher matcher = new TypoTolerantMatcher("*", NameUtil.MatchingCaseSensitivity.NONE, "");
    String[] data = new String[]{"foo", "bar", "buzz"};
    List<String> matched = Arrays.stream(data).filter(matcher::matches).toList();
    List<String> expected = List.of("foo", "bar", "buzz");
    assertEquals(expected, matched);
  }

  @Test
  public void testLongPattern() {
    MinusculeMatcher matcher = NameUtil.buildMatcher("MyLongTestClassName").typoTolerant().build();
    assertFalse(matcher instanceof TypoTolerantMatcher);
    assertTrue(matcher.matches("MyLongTestClassName"));
  }
}
