// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class AnnotationsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "annotations"

  void testAnn1() { doTest() }

  void testAnn2() { doTest() }

  void testAnn3() { doTest() }

  void testAnn4() { doTest() }

  void testAnn5() { doTest() }

  void testAnn6() { doTest() }

  void testAnn7() { doTest() }

  void testClassLiteral() { doTest() }

  void testImportAnn() { doTest() }

  void testPackageAnn() { doTest() }

  void testDefAttribute() { doTest() }

  void testLineFeedAfterRef() { doTest() }

  void testKeywordsAttributes() { doTest() }

  void testMess() { doTest() }

  void testTypeParameters() { doTest() }

  void testUnfinishedReference() { doTest() }
}
