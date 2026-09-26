// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class AddReturnTypeFixTest extends GrIntentionTestCase {
  public AddReturnTypeFixTest() {
    super("Add return type");
  }

  public void testSimple() {
    doTextTest("def f<caret>oo() {}", "void f<caret>oo() {}");
  }

  public void testTypePrams() {
    doTextTest("def <T> f<caret>oo() {}", "def <T> void f<caret>oo() {}");
  }

  public void testReturnPrimitive() {
    doTextTest("def foo() {re<caret>turn 2}", "int foo() {re<caret>turn 2}");
  }

  public void testReturn() {
    doTextTest("def foo() {re<caret>turn \"2\"}", "String foo() {re<caret>turn \"2\"}");
  }

  public void test_at_the_end_of_header_range() {
    doTextTest("def f5()<caret> { def d = 10 }", "void f5() { def d = 10 }");
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "intentions/addReturnType/";
}
