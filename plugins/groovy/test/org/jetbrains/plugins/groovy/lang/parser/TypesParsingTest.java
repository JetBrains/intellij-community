/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class TypesParsingTest extends GroovyParsingTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "types";
  }

  public void testAnn_def1() throws Throwable { doTest(); }
  public void testAnn_def2() throws Throwable { doTest(); }
  public void testAnn_def3() throws Throwable { doTest(); }
  public void testDefault1() throws Throwable { doTest(); }
  public void testDefault2() throws Throwable { doTest(); }
  public void testType1() throws Throwable { doTest(); }
  public void testType10() throws Throwable { doTest(); }
  public void testType11() throws Throwable { doTest(); }
  public void testType12() throws Throwable { doTest(); }
  public void testType2() throws Throwable { doTest(); }
  public void testType3() throws Throwable { doTest(); }
  public void testType4() throws Throwable { doTest(); }
  public void testType5() throws Throwable { doTest(); }
  public void testType6() throws Throwable { doTest(); }
  public void testType7() throws Throwable { doTest(); }
  public void testType8() throws Throwable { doTest(); }
  public void testType9() throws Throwable { doTest(); }

  public void testInnerEnum() throws Throwable { doTest(); }
  public void testNewlineBeforeClassBrace() throws Throwable { doTest(); }

}