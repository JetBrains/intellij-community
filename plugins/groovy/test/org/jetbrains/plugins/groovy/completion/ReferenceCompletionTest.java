/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ReferenceCompletionTest {
  /**
   * The module has the mock JDK of the Groovy test data, as the heavy JUnit 3 test had it.
   * The descriptor allows the access to the JDK root before it creates the JDK, as `JavaModuleFixtureBuilderImpl` does.
   */
  private static final LightProjectDescriptor DESCRIPTOR =
    new DefaultLightProjectDescriptor(() -> IdeaTestUtil.createMockJdk("java 1.7", TestUtils.getMockJdkHome())) {
      @Override
      public void registerSdk(Disposable disposable) {
        VfsRootAccess.allowRootAccess(disposable, TestUtils.getMockJdkHome());
        super.registerSdk(disposable);
      }
    };
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @BeforeEach
  void setUpTestDataPath() {
    groovy.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/oldCompletion/reference");
  }

  @Test public void testEscapedReference() { doTest(); }
  @Test public void testGrvy1021() { doTest(); }
  @Test public void testGrvy1021a() { doTest(); }
  @Test public void testGrvy1021b() { doTest(); }
  @Test public void testGrvy1156() { doTest(); }
  @Test public void testGrvy117() { doTest(); }
  @Test public void testGrvy1272() { doTest(); }
  @Test public void testGRVY1316() { doTest(); }
  @Test public void testGrvy194() { doTest(); }
  @Test public void testGrvy194_1() { doTest(); }
  @Test public void testGRVY223() { doTest(); }
  @Test public void testGrvy487() { doTest(); }
  @Test public void testGrvy491() { doTest(); }
  @Test public void testGrvy76() { doTest(); }
  @Test public void testGrvy959() { doTest(); }
  @Test public void testOnDemand() { doTest(); }
  @Test public void testStaticMethod() { doTest(); }
  @Test public void testThrowVariable() { doTest(); }
  @Test public void testTupleCompl1() { doTest(); }
  @Test public void testTupleLongListAssign() { doTest(); }
  @Test public void testTupleObjCompl() { doTest(); }
  @Test public void testTupleShortListAss() { doTest(); }
  @Test public void testTupleTypedCompl() { doTest(); }
  @Test public void testType() { doTest(); }
  @Test public void testUntyped() { doTest(); }

  /** Completes at the caret of the file of `<test name>.test` and compares the lookup strings with the second part of the file. */
  private void doTest() {
    JavaCodeInsightTestFixture fixture = groovy.get();
    CamelHumpMatcher.forceStartMatching(fixture.getTestRootDisposable());
    final List<String> stringList = TestUtils.readInput(fixture.getTestDataPath() + "/" + groovy.get().getTestName(true) + ".test");
    final String fileName = groovy.get().getTestName(true) + ".groovy";
    fixture.addFileToProject(fileName, stringList.get(0));
    fixture.configureByFile(fileName);

    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    StringBuilder result = new StringBuilder();
    try {
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.completeBasic();

        final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(fixture.getEditor());
        if (lookup != null) {
          List<LookupElement> items = ContainerUtil.sorted(lookup.getItems(), Comparator.comparing(LookupElement::getLookupString));
          for (LookupElement item : items) {
            result.append("\n").append(item.getLookupString());
          }
          LookupManager.hideActiveLookup(fixture.getProject());
        }
      });
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
    }
    assertEquals(StringUtil.trimEnd(stringList.get(1), "\n"), result.toString().trim());
  }
}
