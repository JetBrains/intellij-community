package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class GroovyPsiEventsTest extends LightCodeInsightFixtureTestCase {
  public void testEditingInDocComment() {
    Ref<Boolean> gotIt = new Ref<>(false);
    PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        gotIt.set(true);
      }
    };
    PsiManager manager = getPsiManager();
    try {
      manager.addPsiTreeChangeListener(listener);

      Project project = getProject();
      GroovyFile file = GroovyPsiElementFactory.getInstance(project).createGroovyFile("/** This is doc comment*/class C{}", true, null);
      PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
      Document doc = docManager.getDocument(file);
      assertNotNull(doc);
      CommandProcessor.getInstance().executeCommand(project,
                                                    () -> ApplicationManager.getApplication().runWriteAction(() -> {
                                                      doc.insertString(3, " ");
                                                      docManager.commitDocument(doc);
                                                    }),
                                                    "file text set",
                                                    this
      );
    }
    finally {
      manager.removePsiTreeChangeListener(listener);
    }

    assertTrue(gotIt.get());
  }
}
