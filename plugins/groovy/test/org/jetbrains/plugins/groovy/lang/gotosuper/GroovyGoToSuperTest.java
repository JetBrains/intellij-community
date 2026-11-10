/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.gotosuper;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NavigationTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyGoToSuperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/gotoSuper/");
  }

  @Test
  public void testGts1() {
    doTest();
  }
  @Test
  public void testGts2() {
    doTest();
  }
  @Test
  public void testGts3() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    final CodeInsightActionHandler handler = CodeInsightActions.GOTO_SUPER.forLanguage(GroovyLanguage.INSTANCE);
    EdtTestUtil.runInEdtAndWait(() -> {
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());

      myFixture.checkResult(data.get(1));
    });
  }

}