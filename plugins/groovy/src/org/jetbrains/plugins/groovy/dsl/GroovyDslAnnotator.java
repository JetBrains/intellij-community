/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.unscramble.UnscrambleDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class GroovyDslAnnotator implements Annotator, DumbAware {

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof GroovyFile) {
      final VirtualFile vfile = ((GroovyFile)psiElement).getVirtualFile();
      if (vfile != null && "gdsl".equals(vfile.getExtension()) &&
          (!GroovyDslFileIndex.isActivated(vfile) || FileDocumentManager.getInstance().isFileModified(vfile))) {
        final String reason = GroovyDslFileIndex.getInactivityReason(vfile);
        final String message;
        boolean modified = reason == null || GroovyDslFileIndex.MODIFIED.equals(reason);
        if (modified) {
          message = "DSL descriptor file has been changed and isn't currently executed.";
        } else {
          message = "DSL descriptor file has been disabled due to a processing error.";
        }
        final Annotation annotation = holder.createWarningAnnotation(psiElement, message);
        annotation.setFileLevelAnnotation(true);
        if (!modified) {
          annotation.registerFix(new InvestigateFix(reason));
        }
        annotation.registerFix(new ActivateFix(vfile));
      }
    }
  }

  static void analyzeStackTrace(Project project, String exceptionText) {
    final UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.setText(exceptionText);
    dialog.show();
  }

  private static class InvestigateFix implements IntentionAction {
    private final String myReason;

    public InvestigateFix(String reason) {
      myReason = reason;
    }

    @NotNull
    @Override
    public String getText() {
      return "View details";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Investigate DSL descriptor processing error";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      analyzeStackTrace(project, myReason);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static class ActivateFix implements IntentionAction {
    private final VirtualFile myVfile;

    public ActivateFix(VirtualFile vfile) {
      myVfile = vfile;
    }

    @NotNull
    public String getText() {
      return "Activate back";
    }

    @NotNull
    public String getFamilyName() {
      return "Activate DSL descriptor";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      FileDocumentManager.getInstance().saveAllDocuments();
      GroovyDslFileIndex.activateUntilModification(myVfile);
      DaemonCodeAnalyzer.getInstance(project).restart();
    }

    public boolean startInWriteAction() {
      return false;
    }
  }
}
