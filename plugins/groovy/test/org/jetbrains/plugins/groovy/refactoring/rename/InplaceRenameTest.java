// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrMethodInplaceRenameHandler;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class InplaceRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  private void doTest(String before, String newName, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", before);
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightTestUtil.doInlineRename(new GrMethodInplaceRenameHandler(), newName, myFixture);
      myFixture.checkResult(after);
    });
  }

  @Test
  public void testInplaceRenameMethodAddSpace() {
    doTest("""
             def fo<caret>o() {}
             
             foo()
             """, "foo bar", """
             def 'foo bar'() {}
             
             'foo bar'()
             """);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
}
