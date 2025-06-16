// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

import static org.jetbrains.plugins.groovy.dsl.DslActivationStatus.Status.*;

public final class GroovyDslAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof GroovyFile groovyFile)) return;

    if (!GrFileIndexUtil.isGroovySourceFile(groovyFile)) return;
    
    final VirtualFile vfile = groovyFile.getVirtualFile();
    if (!GdslUtil.GDSL_FILTER.value(vfile)) return;
    
    final DslActivationStatus.Status status = GroovyDslFileIndex.getStatus(vfile);
    if (status == ACTIVE) return;

    final String message = status == MODIFIED
                           ? GroovyBundle.message("inspection.message.dsl.descriptor.file.has.been.changed.and.isnt.currently.executed")
                           : GroovyBundle.message("inspection.message.dsl.descriptor.file.has.been.disabled.due.to.processing.error");

    AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WARNING, message)
      .fileLevel()
      .withFix(new ActivateFix(vfile));
    if (status == ERROR) {
      final String error = GroovyDslFileIndex.getError(vfile);
      if (error != null) {
        builder = builder.withFix(GroovyQuickFixFactory.getInstance().createInvestigateFix(error));
      }
    }
    builder.create();
  }

  private static class ActivateFix implements IntentionAction {
    private final VirtualFile myVfile;

    ActivateFix(VirtualFile vfile) {
      myVfile = vfile;
    }

    @Override
    public @NotNull String getText() {
      return GroovyBundle.message("intention.name.activate.back");
    }

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.activate.dsl.descriptor");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
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
