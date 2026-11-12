// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.annotations;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

@TestApplication
public class AddAnnotationValueIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, "Add 'value='", null);

  @Test
  public void test_add_value() {
    grIntention.get().doTextTest("""
                 @Anno(<caret>arg)
                 def foo() {}
                 """, """
                 @Anno(<caret>value = arg)
                 def foo() {}
                 """);
  }

  @Test
  public void test_add_value_inner() {
    grIntention.get().doTextTest("""
                 @Anno(@Inner(<caret>arg))
                 def foo() {}
                 """, """
                 @Anno(@Inner(<caret>value = arg))
                 def foo() {}
                 """);
  }

  @Test
  public void test_add_value_inner_annotation() {
    grIntention.get().doTextTest("""
                 @Anno(<caret>@Inner(arg))
                 def foo() {}
                 """, """
                 @Anno(<caret>value = @Inner(arg))
                 def foo() {}
                 """);
  }

  @Test
  public void test_dont_add_value_if_name_defined() {
    grIntention.get().doAntiTest("""
                 @Anno(name = <caret>arg)
                 def foo() {}
                 """);
  }

  @Test
  public void test_dont_add_value_if_many_arguments() {
    grIntention.get().doAntiTest("""
                 @Anno(<caret>arg1, arg2)
                 def foo() {}
                 """);
  }
}
