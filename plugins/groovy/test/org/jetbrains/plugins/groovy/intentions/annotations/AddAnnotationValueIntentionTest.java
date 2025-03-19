// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.annotations;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;

public class AddAnnotationValueIntentionTest extends GrIntentionTestCase {
  public AddAnnotationValueIntentionTest() {
    super("Add 'value='");
  }

  public void test_add_value() {
    doTextTest("""
                 @Anno(<caret>arg)
                 def foo() {}
                 """, """
                 @Anno(<caret>value = arg)
                 def foo() {}
                 """);
  }

  public void test_add_value_inner() {
    doTextTest("""
                 @Anno(@Inner(<caret>arg))
                 def foo() {}
                 """, """
                 @Anno(@Inner(<caret>value = arg))
                 def foo() {}
                 """);
  }

  public void test_add_value_inner_annotation() {
    doTextTest("""
                 @Anno(<caret>@Inner(arg))
                 def foo() {}
                 """, """
                 @Anno(<caret>value = @Inner(arg))
                 def foo() {}
                 """);
  }

  public void test_dont_add_value_if_name_defined() {
    doAntiTest("""
                 @Anno(name = <caret>arg)
                 def foo() {}
                 """);
  }

  public void test_dont_add_value_if_many_arguments() {
    doAntiTest("""
                 @Anno(<caret>arg1, arg2)
                 def foo() {}
                 """);
  }
}
