// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrIntroduceParameterInClosureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "refactoring/introduceParameterInClosure/");
  }

  private void doTest(final int replaceFieldsWithGetters,
                      final boolean removeUnusedParameters,
                      final boolean declareFinal,
                      @Nullable final String conflicts,
                      final boolean generateDelegate) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");

    EdtTestUtil.runInEdtAndWait(() -> {
      GrIntroduceParameterTest.execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate,
                                       myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
      myFixture.getEditor().getSelectionModel().removeSelection();
    });

    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  private void doTest(final int replaceFieldsWithGetters,
                      final boolean removeUnusedParameters,
                      final boolean declareFinal,
                      @Nullable final String conflicts,
                      final boolean generateDelegate,
                      String before,
                      String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("before.groovy", before);

    EdtTestUtil.runInEdtAndWait(() -> {
      GrIntroduceParameterTest.execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate,
                                       myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
      myFixture.getEditor().getSelectionModel().removeSelection();
    });

    myFixture.checkResult(after);
  }

  @Test
  public void testSimpleClosure() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false);
  }

  @Test
  public void testRemoveUnusedParam() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, null, false);
  }

  @Test
  public void testLocalVarUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false);
  }

  @Test
  public void testField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, null, false);
  }

  @Test
  public void testReplaceWithGetter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, null, false);
  }

  @Test
  public void testGetter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, true, null, false);
  }

  @Test
  public void testReplaceGetterCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, null, false);
  }

  @Test
  public void testClosureRefWithoutCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false);
  }

  @Test
  public void testClosureCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false);
  }

  @Test
  public void testVarAssignedToClosure() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false);
  }

  @Test
  public void testCorrectOccurrencesForLocalVar() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false);
  }

  /*public void testDelegate() {
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, true);
    }
  
    public void testDelegateRemoveUnusedParam() {
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, null, true);
    }*/

  @Test
  public void testStringPart0() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      def cl = {
          print 'a<selection>b</selection>c'
      }
      cl()
      """, """
             def cl = { String anObject ->
                 print 'a' + anObject<caret> + 'c'
             }
             cl('b')
             """);
  }
}