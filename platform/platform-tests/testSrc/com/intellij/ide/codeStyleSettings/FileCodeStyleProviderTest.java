// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileCodeStyleProvider;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.application.ex.PathManagerEx.getTestDataPath;

public class FileCodeStyleProviderTest extends UsefulTestCase {

  private CodeStyleSettings myTestSettings;

  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final IdeaProjectTestFixture fixture = factory.createLightFixtureBuilder(getTestName(false)).getFixture();
    myFixture = factory.createCodeInsightFixture(fixture);

    myFixture.setTestDataPath(getTestDataPath());

    myFixture.setUp();
    myTestSettings =  CodeStyle.createTestSettings();

    FileCodeStyleProvider.EP_NAME.getPoint().registerExtension(new TestCodeStyleProvider(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
      myTestSettings = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  private class TestCodeStyleProvider implements FileCodeStyleProvider {
    @Override
    public CodeStyleSettings getSettings(@NotNull PsiFile file) {
      return myTestSettings;
    }
  }


  public void testFileCodeStyleProvider() {
    PsiFile file = myFixture.configureByText("a.java", "class Foo {}");
    CodeStyle.dropTemporarySettings(file.getProject());
    CodeStyleSettings settings = CodeStyle.getSettings(file);
    assertSame(myTestSettings, settings);
  }

}
