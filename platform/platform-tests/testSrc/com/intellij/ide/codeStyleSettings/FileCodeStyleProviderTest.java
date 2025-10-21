// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileCodeStyleProvider;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

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
    static final AtomicInteger counter = new AtomicInteger();
    @Override
    public CodeStyleSettings getSettings(@NotNull PsiFile file) {
      counter.incrementAndGet();
      return myTestSettings;
    }
  }


  public void testFileCodeStyleProvider() {
    PsiFile psiFile = myFixture.configureByText("a.java", "class Foo {}");
    CodeStyle.dropTemporarySettings(psiFile.getProject());
    CodeStyleSettings settings = CodeStyle.getSettings(psiFile);
    assertSame(myTestSettings, settings);
  }

  public void testCodeStyleSettingsDoNotRecomputedOnEachDocumentChangedBecauseItsCrazyExpensive() {
    PsiFile psiFile = myFixture.configureByText("a.java", "class Foo {}");
    CodeStyle.dropTemporarySettings(psiFile.getProject());
    CodeStyleSettings settings = CodeStyle.getSettings(psiFile);
    assertSame(myTestSettings, settings);
    assertTrue(TestCodeStyleProvider.counter.get()>0);
    Document document = psiFile.getFileDocument();
    myTestSettings.getModificationTracker().incModificationCount();

    TestCodeStyleProvider.counter.set(0);
    for (int i=0; i<10; i++) {
      myTestSettings.getModificationTracker().incModificationCount();
      WriteCommandAction.runWriteCommandAction(psiFile.getProject(), () -> document.insertString(0, "xxx"));
      assertEquals(0, TestCodeStyleProvider.counter.get());
    }
    myTestSettings.getModificationTracker().incModificationCount();
    CodeStyleSettings settings2 = CodeStyle.getSettings(psiFile);
    assertSame(myTestSettings, settings2);
    assertTrue(TestCodeStyleProvider.counter.get()>0);
  }
}
