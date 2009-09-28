/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class GenericsParsingTest extends GroovyParsingTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "generics";
  }

  public void testErr1() throws Throwable { doTest(); }
  public void testErr2() throws Throwable { doTest(); }
  public void testErr3() throws Throwable { doTest(); }
  public void testGenmethod1() throws Throwable { doTest(); }
  public void testGenmethod2() throws Throwable { doTest(); }
  public void testGenmethod3() throws Throwable { doTest(); }
  public void testGenmethod4() throws Throwable { doTest(); }
  public void testTypeargs1() throws Throwable { doTest(); }
  public void testTypeparam1() throws Throwable { doTest(); }
  public void testTypeparam2() throws Throwable { doTest(); }
  public void testTypeparam3() throws Throwable { doTest(); }

}