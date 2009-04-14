/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class CommentsParsingTest extends GroovyParsingTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "comments";
  }

  public void testBrown() throws Throwable { doTest(); }
  public void testError1205() throws Throwable { doTest(); }
  public void testMax() throws Throwable { doTest(); }
  public void testNls1() throws Throwable { doTest(); }
  public void testNls2() throws Throwable { doTest(); }
  public void testRocher3() throws Throwable { doTest(); }
  
}
