/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class AnnotationsParsingTest extends GroovyParsingTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "annotations";
  }

  public void testAnn1() throws Throwable { doTest(); }
  public void testAnn2() throws Throwable { doTest(); }
  public void testAnn3() throws Throwable { doTest(); }
  public void testAnn4() throws Throwable { doTest(); }
  public void testAnn5() throws Throwable { doTest(); }
  public void testAnn6() throws Throwable { doTest(); }
  public void testAnn7() throws Throwable { doTest(); }
  public void testClassLiteral() throws Throwable { doTest(); }
  public void testImportAnn() throws Throwable { doTest(); }
  public void testPackageAnn() throws Throwable { doTest(); }
}