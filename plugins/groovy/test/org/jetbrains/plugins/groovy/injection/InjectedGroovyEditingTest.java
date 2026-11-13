// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.injection;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class InjectedGroovyEditingTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.JAVA_LATEST);

  @BeforeEach
  public void setUp() {
    new MyTestInjector(groovy.get().getPsiManager()).injectAll(groovy.get().getTestRootDisposable());
  }

  @Test
  public void testBackspaceAfterQuoteInsideStringLiteral2() {
    JavaCodeInsightTestFixture fixture = groovy.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByFile("injected/groovy.java");
      // LightPlatformCodeInsightTestCase opens the injected editor at the caret, so the old test checked it.
      assertInstanceOf(EditorWindow.class, InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(fixture.getEditor(), fixture.getFile()));
      assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty();

      // As after bringRealEditorBack of the base, the text is the text of the host file.
      PsiFile hostFile = InjectedLanguageManager.getInstance(fixture.getProject()).getTopLevelFile(fixture.getFile());
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
