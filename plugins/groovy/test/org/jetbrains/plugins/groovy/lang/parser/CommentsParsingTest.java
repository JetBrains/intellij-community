// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

public class CommentsParsingTest extends GroovyParsingTestCase {
  @Override
  public String getBasePath() {
    return super.getBasePath() + "comments";
  }

  public void testBrown() { doTest(); }

  public void testError1205() { doTest(); }

  public void testMax() { doTest(); }

  public void testNls1() { doTest(); }

  public void testNls2() { doTest(); }

  public void testRocher3() { doTest(); }

  public void testAfterIdentifier() { doTest(); }
}
