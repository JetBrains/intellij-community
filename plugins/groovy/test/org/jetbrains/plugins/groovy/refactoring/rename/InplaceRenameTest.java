// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrMethodInplaceRenameHandler;

public class InplaceRenameTest extends LightGroovyTestCase {
  private void doTest(String before, String newName, String after) {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("_.groovy", before);
    CodeInsightTestUtil.doInlineRename(new GrMethodInplaceRenameHandler(), newName, fixture);
    fixture.checkResult(after);
  }

  public void testInplaceRenameMethodAddSpace() {
    doTest("""
             def fo<caret>o() {}
             
             foo()
             """, "foo bar", """
             def 'foo bar'() {}
             
             'foo bar'()
             """);
  }

  public void testInplaceRenameMethodRemoveSpaceSingleSingleQuote() {
    doTest("""
             def 'foo<caret> bar'() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo", """
             def foo() {}
             
             foo()
             foo()
             foo()
             foo()
             """);
  }

  public void testInplaceRenameMethodRemoveSpaceTripleSingleQuote() {
    doTest("""
             def '''foo<caret> bar'''() {}
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo", """
             def foo() {}
             foo()
             foo()
             foo()
             foo()
             """);
  }

  public void testInplaceRenameMethodRemoveSpaceSingeDoubleQuote() {
    doTest("""
             def "foo<caret> bar"() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo", """
             def foo() {}
             
             foo()
             foo()
             foo()
             foo()
             """);
  }

  public void testInplaceRenameMethodRemoveSpaceTripleDoubleQoute() {
    doTest("""
             def ""\"foo<caret> bar""\"() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo", """
             def foo() {}
             
             foo()
             foo()
             foo()
             foo()
             """);
  }

  public void testInplaceRenameMethodSingleSingleQuote() {
    doTest("""
             def 'foo<caret> bar'() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo baz", """
             def 'foo baz'() {}
             
             'foo baz'()
             'foo baz'()
             'foo baz'()
             'foo baz'()
             """);
  }

  public void testInplaceRenameMethodTripleSingleQuote() {
    doTest("""
             def '''foo<caret> bar'''() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo baz", """
             def 'foo baz'() {}
             
             'foo baz'()
             'foo baz'()
             'foo baz'()
             'foo baz'()
             """);
  }

  public void testInplaceRenameMethodSingleDoubleQuote() {
    doTest("""
             def "foo<caret> bar"() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo baz", """
             def 'foo baz'() {}
             
             'foo baz'()
             'foo baz'()
             'foo baz'()
             'foo baz'()
             """);
  }

  public void testInplaceRenameMethodTripleDoubleQuote() {
    doTest("""
             def ""\"foo<caret> bar""\"() {}
             
             'foo bar'()
             '''foo bar'''()
             "foo bar"()
             ""\"foo bar""\"()
             """, "foo baz", """
             def 'foo baz'() {}
             
             'foo baz'()
             'foo baz'()
             'foo baz'()
             'foo baz'()
             """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
