// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

public final class GroovyOptimizeImportsFix implements ModCommandAction {
  private static final Logger LOG = Logger.getInstance(GroovyOptimizeImportsFix.class);
  private final boolean onTheFly;

  GroovyOptimizeImportsFix(boolean onTheFly) {
    this.onTheFly = onTheFly;
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("optimize.imports");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiFile psiFile = context.file();
    if (psiFile instanceof GroovyFile && (!onTheFly || timeToOptimizeImports((GroovyFile)psiFile, context.offset()))) {
      return Presentation.of(GroovyBundle.message("optimize.all.imports"));
    }
    return null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiFile file = context.file();
    ModCommand command = ModCommand.psiUpdate(file, groovyFile -> new GroovyImportOptimizer().processFile(groovyFile).run());
    if (command.isEmpty()) {
      VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
      LOG.error("Import optimizer hasn't optimized any imports", new Attachment(virtualFile.getPath(), file.getText()));
    }
    return command;
  }

  private static boolean timeToOptimizeImports(GroovyFile myFile, int offset) {
    if (!CodeInsightWorkspaceSettings.getInstance(myFile.getProject()).isOptimizeImportsOnTheFly()) {
      return false;
    }
    // if we stand inside import statements, do not optimize
    final VirtualFile vfile = myFile.getVirtualFile();
    if (vfile != null && ProjectRootManager.getInstance(myFile.getProject()).getFileIndex().isInSource(vfile)) {
      final GrImportStatement[] imports = myFile.getImportStatements();
      if (imports.length > 0 &&
          imports[0].getTextRange().getStartOffset() <= offset &&
          offset <= imports[imports.length - 1].getTextRange().getEndOffset()) {
        return false;
      }
    }

    if (!DaemonCodeAnalyzer.getInstance(myFile.getProject()).isHighlightingAvailable(myFile)) {
      return false;
    }
    Document myDocument = myFile.getFileDocument();
    boolean errors = containsErrorsPreventingOptimize(myFile, myDocument);

    // computed in GroovyPostHighlightingPass.doCollectInformation()
    boolean isInContent = true;
    return !errors && DaemonListeners.canChangeFileSilently(myFile, isInContent, ThreeState.UNSURE);
  }

  private static boolean containsErrorsPreventingOptimize(GroovyFile myFile, Document myDocument) {
    // ignore unresolved imports errors
    final TextRange ignoreRange;
    final GrImportStatement[] imports = myFile.getImportStatements();
    if (imports.length != 0) {
      final int start = imports[0].getTextRange().getStartOffset();
      final int end = imports[imports.length - 1].getTextRange().getEndOffset();
      ignoreRange = new TextRange(start, end);
    } else {
      ignoreRange = TextRange.EMPTY_RANGE;
    }

    return !DaemonCodeAnalyzerEx
      .processHighlights(myDocument, myFile.getProject(), HighlightSeverity.ERROR, 0, myDocument.getTextLength(), error -> {
        int infoStart = error.getActualStartOffset();
        int infoEnd = error.getActualEndOffset();

        return ignoreRange.containsRange(infoStart, infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
      });
  }
}
