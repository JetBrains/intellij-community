// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class LineNumberBeforeUpdateStressTest extends LightPlatformTestCase {
  private static final Logger LOG = Logger.getInstance(LineNumberBeforeUpdateStressTest.class);

  private static final int ITERATIONS = 10_000;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed
  private static final String CHARS_TO_USE = " \r\n";
  private static final int MAX_TEXT_LENGTH = 10;
  private long mySeed;
  private final Random myRandom = new Random() {{
    //noinspection ConstantConditions
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};

  public void testRandomUpdates() {
    LOG.debug("Seed is " + mySeed);
    int i = 0;
    try {
      for (; i < ITERATIONS; i++) {
        Document document = new DocumentImpl(generateRandomSequence(), true, true);
        document.addDocumentListener(new ValidatingListener());
        int o1 = myRandom.nextInt(document.getTextLength() + 1);
        int o2 = myRandom.nextInt(document.getTextLength() + 1);
        CharSequence replacement = generateRandomSequence();
        document.replaceString(Math.min(o1, o2), Math.max(o1, o2), replacement);
      }
    }
    catch (Throwable t) {
      String message = "Failed when run with seed=" + mySeed + " in iteration " + i;
      System.err.println(message);
      throw new RuntimeException(message, t);
    }
  }

  private CharSequence generateRandomSequence() {
    int textLength = myRandom.nextInt(MAX_TEXT_LENGTH);
    StringBuilder text = new StringBuilder();
    for (int j = 0; j < textLength; j++) {
      text.append(CHARS_TO_USE.charAt(myRandom.nextInt(CHARS_TO_USE.length())));
    }
    return text;
  }

  private static class ValidatingListener implements DocumentListener {
    private int[] expectedResults;

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      Document document = event.getDocument();
      int textLength = document.getTextLength();
      expectedResults = new int[textLength + 1];
      for (int i = 0; i <= textLength; i++) {
        expectedResults[i] = document.getLineNumber(i);
      }
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      for (int i = 0; i < expectedResults.length; i++) {
        assertEquals("Wrong result for offset " + i, expectedResults[i], ((DocumentEventImpl)event).getLineNumberBeforeUpdate(i));
      }
    }
  }
}
