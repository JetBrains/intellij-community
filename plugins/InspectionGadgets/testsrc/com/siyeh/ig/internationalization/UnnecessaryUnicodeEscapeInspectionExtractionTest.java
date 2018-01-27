// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.internationalization;

import com.intellij.testFramework.UsefulTestCase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class UnnecessaryUnicodeEscapeInspectionExtractionTest extends UsefulTestCase {

  public void testExtraction() {
    assertExtractedSequences("No Unicode escape sequence here." /* none */);
    assertExtractedSequences("\\u0041", "\\u0041");
    assertExtractedSequences("\\\\u0041" /* none */); // Backslash followed by the characters "u0041"
    assertExtractedSequences("\\\\\\u2013", "\\u2013"); // Backslash followed by escape sequence
    assertExtractedSequences("\\u004" /* none */); // Too short
    assertExtractedSequences("\\u004g" /* none */); // Invalid hex character
    assertExtractedSequences("\\uuuuuu0041", "\\uuuuuu0041"); // This is allowed
  }

  private static void assertExtractedSequences(String javaSourceCode, String... expectedSequences) {
    final List<String> sequences = new ArrayList<>();
    UnnecessaryUnicodeEscapeInspection.forEachUnicodeSequence(
      javaSourceCode,
      StandardCharsets.UTF_8,
      new UnnecessaryUnicodeEscapeInspection.Callback() {
        @Override
        public boolean foundSequence(String text, int start, int end, char codeUnit) {
          return sequences.add(text.substring(start, end));
        }
      });
    assertOrderedEquals(sequences, expectedSequences);
  }
}
