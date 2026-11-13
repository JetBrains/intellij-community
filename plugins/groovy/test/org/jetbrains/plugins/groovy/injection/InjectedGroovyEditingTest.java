// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.injection;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class InjectedGroovyEditingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @BeforeEach
  void setUpTestDataPath() {
    groovy.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath());
  }

  @BeforeEach
  public void setUp() {
    new MyTestInjector(groovy.get().getPsiManager()).injectAll(groovy.get().getTestRootDisposable());
  }

  @Test
  public void testBackspaceAfterQuoteInsideStringLiteral2() {
    JavaCodeInsightTestFixture fixture = groovy.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByFile("injected/groovy.java");
      EditorWindow injectedEditor = assertInstanceOf(EditorWindow.class, fixture.getEditor());
      assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty();

      // As bringRealEditorBack of the old base, the typing goes to the editor of the Java file.
      PsiFile hostFile = InjectedLanguageManager.getInstance(fixture.getProject()).getTopLevelFile(fixture.getFile());
      fixture.openFileInEditor(hostFile.getVirtualFile());
      assertSame(injectedEditor.getDelegate(), fixture.getEditor());
      String oldText = hostFile.getText();
      fixture.type("\"");
      PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
      fixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
      PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
      assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertEquals(oldText, hostFile.getText());
    });
  }
}
