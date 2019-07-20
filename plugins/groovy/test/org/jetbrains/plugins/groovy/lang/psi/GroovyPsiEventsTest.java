// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class GroovyPsiEventsTest extends LightJavaCodeInsightFixtureTestCase {
  public void testEditingInDocComment() {
    Ref<Boolean> gotIt = new Ref<>(false);
    PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        gotIt.set(true);
      }
    };
    getPsiManager().addPsiTreeChangeListener(listener, getTestRootDisposable());
    Project project = getProject();
    GroovyFile file = GroovyPsiElementFactory.getInstance(project).createGroovyFile("/** This is doc comment*/class C{}", true, null);
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    Document doc = docManager.getDocument(file);
    assertNotNull(doc);
    CommandProcessor.getInstance().executeCommand(
      project,
      () -> ApplicationManager.getApplication().runWriteAction(() -> {
        doc.insertString(3, " ");
        docManager.commitDocument(doc);
      }),
      "file text set",
      this
    );
    assertTrue(gotIt.get());
  }
}
