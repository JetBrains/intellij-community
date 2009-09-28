/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class PackagingParsingTest extends GroovyParsingTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "packaging";
  }

  public void testPack1() throws Throwable { doTest(); }
  public void testPack2() throws Throwable { doTest(); }
  public void testPack3() throws Throwable { doTest(); }
  public void testPack4() throws Throwable { doTest(); }
  public void testPack5() throws Throwable { doTest(); }
  public void testPack6() throws Throwable { doTest(); }
  public void testPack7() throws Throwable { doTest(); }
  public void testPack8() throws Throwable { doTest(); }

}