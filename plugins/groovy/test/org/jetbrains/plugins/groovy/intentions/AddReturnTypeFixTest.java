// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class AddReturnTypeFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, "Add return type", TestUtils.getTestDataPath() + "intentions/addReturnType/");

  @Test
  public void testSimple() {
    grIntention.get().doTextTest("def f<caret>oo() {}", "void f<caret>oo() {}");
  }

  @Test
  public void testTypePrams() {
    grIntention.get().doTextTest("def <T> f<caret>oo() {}", "def <T> void f<caret>oo() {}");
  }

  @Test
  public void testReturnPrimitive() {
    grIntention.get().doTextTest("def foo() {re<caret>turn 2}", "int foo() {re<caret>turn 2}");
  }

  @Test
  public void testReturn() {
    grIntention.get().doTextTest("def foo() {re<caret>turn \"2\"}", "String foo() {re<caret>turn \"2\"}");
  }

  @Test
  public void test_at_the_end_of_header_range() {
    grIntention.get().doTextTest("def f5()<caret> { def d = 10 }", "void f5() { def d = 10 }");
  }
}
