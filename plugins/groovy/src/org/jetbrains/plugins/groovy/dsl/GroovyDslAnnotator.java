package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.Annotation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class GroovyDslAnnotator implements Annotator {

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    if (psiElement instanceof GroovyFile) {
      final GroovyFile file = (GroovyFile)psiElement;
      final VirtualFile vfile = file.getVirtualFile();
      if (vfile != null && "gdsl".equals(vfile.getExtension()) && !GroovyDslFileIndex.isActivated(vfile)) {
        final Annotation annotation = holder.createWarningAnnotation(file, "DSL descriptor file has been changed and isn't currently executed. Click to activate it back.");
        annotation.setFileLevelAnnotation(true);
        annotation.registerFix(new IntentionAction() {
          @NotNull
          public String getText() {
            return "Activate";
          }

          @NotNull
          public String getFamilyName() {
            return "Activate DSL descriptor";
          }

          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
          }

          public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            GroovyDslFileIndex.activateUntilModification(vfile);
            DaemonCodeAnalyzer.getInstance(project).restart();
          }

          public boolean startInWriteAction() {
            return false;
          }
        });
      }
    }
  }
}
