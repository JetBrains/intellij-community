// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyHighlightUsagesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  private static final SeveritiesProvider SEVERITIES_PROVIDER = new SeveritiesProvider() {
    @Override
    public @NotNull List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {
      return List.of(HighlightInfoType.ELEMENT_UNDER_CARET_READ, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE);
    }
  };

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "highlighting/usages/");
    myFixture.setReadEditorMarkupModel(true);
  }

  private void doTest(boolean directoryTest) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      SeveritiesProvider.EP_NAME.getPoint().registerExtension(SEVERITIES_PROVIDER, myFixture.getTestRootDisposable());
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.getProject(), () -> {
        String name = LightGroovyFixtures.groovyTestName(myFixture);
        if (directoryTest) {
          myFixture.copyDirectoryToProject(name, "");
          myFixture.configureByFile(name + "/test.groovy");
        }
        else {
          myFixture.configureByFile(name + ".groovy");
        }

        myFixture.checkHighlighting();
      });
    });
  }

  private void doTest() {
    doTest(false);
  }

  @Test
  public void testConstructorUsages1() { doTest(); }

  @Test
  public void testConstructorUsages2() { doTest(); }

  @Test
  public void testConstructorUsages3() { doTest(); }

  @Test
  public void testConstructorUsages4() { doTest(true); }

  @Test
  public void testClassUsages1() { doTest(); }

  @Test
  public void testClassUsages2() { doTest(); }

  @Test
  public void testBindingVariable() { doTest(); }
}
