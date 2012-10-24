/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
public class AnnotationsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "annotations"

  public void testAnn1() { doTest() }
  public void testAnn2() { doTest() }
  public void testAnn3() { doTest() }
  public void testAnn4() { doTest() }
  public void testAnn5() { doTest() }
  public void testAnn6() { doTest() }
  public void testAnn7() { doTest() }
  public void testClassLiteral() { doTest() }
  public void testImportAnn() { doTest() }
  public void testPackageAnn() { doTest() }
  public void testDefAttribute() {doTest()}
}