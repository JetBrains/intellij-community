package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.testFramework.PsiTestCase;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;

/**
 * @author ven
 */
public class PsiEventsTest extends PsiTestCase {
  public void testEditingInDocComment() throws  Exception {
    final Ref<Boolean> gotIt = new Ref<Boolean>(false);
    getPsiManager().addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      public void childReplaced(PsiTreeChangeEvent event) {
        gotIt.set(true);
      }
    });

    GroovyFile file = GroovyPsiElementFactory.getInstance(myProject).createGroovyFile("/** This is doc comment*/class C{}", true, null);
    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(myProject);
    final Document doc = docManager.getDocument(file);
    assertNotNull(doc);
    CommandProcessor.getInstance().executeCommand(myProject,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              doc.insertString(3, " ");
              docManager.commitDocument(doc);
            }
          });
        }
      },
      "file text set",
      this
    );


    assertTrue(gotIt.get());
  }
}
