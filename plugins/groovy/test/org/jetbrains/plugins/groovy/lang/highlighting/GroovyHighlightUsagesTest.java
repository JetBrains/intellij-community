// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class GroovyHighlightUsagesTest extends LightGroovyTestCase {
  private static final SeveritiesProvider SEVERITIES_PROVIDER = new SeveritiesProvider() {
    @Override
    public @NotNull List<HighlightInfoType> getSeveritiesHighlightInfoTypes() {
      return List.of(HighlightInfoType.ELEMENT_UNDER_CARET_READ, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE);
    }
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setReadEditorMarkupModel(true);
  }

  private void doTest(boolean directoryTest) {
    SeveritiesProvider.EP_NAME.getPoint().registerExtension(SEVERITIES_PROVIDER, getTestRootDisposable());
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), () -> {
      String name = getTestName();
      JavaCodeInsightTestFixture fixture = getFixture();
      if (directoryTest) {
        fixture.copyDirectoryToProject(name, "");
        fixture.configureByFile(name + "/test.groovy");
      }
      else {
        fixture.configureByFile(name + ".groovy");
      }

      fixture.checkHighlighting();
    });
  }

  private void doTest() {
    doTest(false);
  }

  public void testConstructorUsages1() { doTest(); }

  public void testConstructorUsages2() { doTest(); }

  public void testConstructorUsages3() { doTest(); }

  public void testConstructorUsages4() { doTest(true); }

  public void testClassUsages1() { doTest(); }

  public void testClassUsages2() { doTest(); }

  public void testBindingVariable() { doTest(); }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/usages/";
  }
}
