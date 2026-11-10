// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyPsiEventsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testEditingInDocComment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Ref<Boolean> gotIt = new Ref<>(false);
    PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        gotIt.set(true);
      }
    };
    PsiManager.getInstance(myFixture.getProject()).addPsiTreeChangeListener(listener, myFixture.getTestRootDisposable());
    Project project = myFixture.getProject();
    GroovyFile file = ReadAction.computeBlocking(() -> GroovyPsiElementFactory.getInstance(project).createGroovyFile("/** This is doc comment*/class C{}", true, null));
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    Document doc = ReadAction.computeBlocking(() -> docManager.getDocument(file));
    assertNotNull(doc);
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(
        project,
        () -> ApplicationManager.getApplication().runWriteAction(() -> {
          doc.insertString(3, " ");
          docManager.commitDocument(doc);
        }),
        "file text set",
        this
      );
    });
    assertTrue(gotIt.get());
  }
}
