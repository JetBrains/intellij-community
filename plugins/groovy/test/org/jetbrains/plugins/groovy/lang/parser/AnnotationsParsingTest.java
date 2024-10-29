// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

public class AnnotationsParsingTest extends GroovyParsingTestCase {
  public void testAnn1() { doTest(); }

  public void testAnn2() { doTest(); }

  public void testAnn3() { doTest(); }

  public void testAnn4() { doTest(); }

  public void testAnn5() { doTest(); }

  public void testAnn6() { doTest(); }

  public void testAnn7() { doTest(); }

  public void testClassLiteral() { doTest(); }

  public void testImportAnn() { doTest(); }

  public void testPackageAnn() { doTest(); }

  public void testDefAttribute() { doTest(); }

  public void testLineFeedAfterRef() { doTest(); }

  public void testKeywordsAttributes() { doTest(); }

  public void testMess() { doTest(); }

  public void testTypeParameters() { doTest(); }

  public void testUnfinishedReference() { doTest(); }

  public void testEmptyArrayValue() { doTest(); }

  public void testTrailingCommaInArrayValue() { doTest(); }

  public void testMethodParameters() { doTest(); }

  @Override
  public final String getBasePath() {
    return super.getBasePath() + "annotations";
  }
}
