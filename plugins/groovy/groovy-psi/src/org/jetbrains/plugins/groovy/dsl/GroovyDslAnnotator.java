/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

import static org.jetbrains.plugins.groovy.dsl.DslActivationStatus.Status.*;

/**
 * @author peter
 */
public class GroovyDslAnnotator implements Annotator, DumbAware {

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof GroovyFile)) return;

    final GroovyFile groovyFile = (GroovyFile)psiElement;
    if (!GrFileIndexUtil.isGroovySourceFile(groovyFile)) return;
    
    final VirtualFile vfile = groovyFile.getVirtualFile();
    if (!GdslUtil.GDSL_FILTER.value(vfile)) return;
    
    final DslActivationStatus.Status status = GroovyDslFileIndex.getStatus(vfile);
    if (status == ACTIVE) return;

    final String message = status == MODIFIED
                           ? "DSL descriptor file has been changed and isn't currently executed."
                           : "DSL descriptor file has been disabled due to a processing error.";

    final Annotation annotation = holder.createWarningAnnotation(psiElement, message);
    annotation.setFileLevelAnnotation(true);
    if (status == ERROR) {
      final String error = GroovyDslFileIndex.getError(vfile);
      if (error != null) {
        annotation.registerFix(GroovyQuickFixFactory.getInstance().createInvestigateFix(error));
      }
    }
    annotation.registerFix(new ActivateFix(vfile));
  }

  private static class ActivateFix implements IntentionAction {
    private final VirtualFile myVfile;

    public ActivateFix(VirtualFile vfile) {
      myVfile = vfile;
    }

    @Override
    @NotNull
    public String getText() {
      return "Activate back";
    }

    @Override
    @NotNull
    public String getFamilyName() {
      //noinspection DialogTitleCapitalization
      return "Activate DSL Descriptor";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      FileDocumentManager.getInstance().saveAllDocuments();
      GroovyDslFileIndex.activate(myVfile);
      DaemonCodeAnalyzer.getInstance(project).restart();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
