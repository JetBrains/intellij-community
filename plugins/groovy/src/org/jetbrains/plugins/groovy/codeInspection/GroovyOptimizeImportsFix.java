// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyPostHighlightingPass;
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

public final class GroovyOptimizeImportsFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(GroovyPostHighlightingPass.class);
  private final boolean onTheFly;

  GroovyOptimizeImportsFix(boolean onTheFly) {
    this.onTheFly = onTheFly;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final Runnable optimize = new GroovyImportOptimizer().processFile(psiFile);
    invokeOnTheFlyImportOptimizer(optimize, psiFile, editor);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    String originalText = psiFile.getText();
    final Runnable optimize = new GroovyImportOptimizer().processFile(psiFile);
    optimize.run();
    String newText = psiFile.getText();
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, originalText, newText);
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("optimize.all.imports");
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("optimize.imports");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return psiFile instanceof GroovyFile && (!onTheFly || timeToOptimizeImports((GroovyFile)psiFile, editor));
  }

  private boolean timeToOptimizeImports(GroovyFile myFile, Editor editor) {
    if (!CodeInsightWorkspaceSettings.getInstance(myFile.getProject()).isOptimizeImportsOnTheFly()) {
      return false;
    }
    if (onTheFly && editor != null) {
      // if we stand inside import statements, do not optimize
      final VirtualFile vfile = myFile.getVirtualFile();
      if (vfile != null && ProjectRootManager.getInstance(myFile.getProject()).getFileIndex().isInSource(vfile)) {
        final GrImportStatement[] imports = myFile.getImportStatements();
        if (imports.length > 0) {
          final int offset = editor.getCaretModel().getOffset();
          if (imports[0].getTextRange().getStartOffset() <= offset && offset <= imports[imports.length - 1].getTextRange().getEndOffset()) {
            return false;
          }
        }
      }
    }

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myFile.getProject());
    if (!codeAnalyzer.isHighlightingAvailable(myFile)) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(myFile)) return false;
    Document myDocument = PsiDocumentManager.getInstance(myFile.getProject()).getDocument(myFile);
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

  public static void invokeOnTheFlyImportOptimizer(final @NotNull Runnable runnable,
                                                   final @NotNull PsiFile file,
                                                   final @NotNull Editor editor) {
    final long stamp = editor.getDocument().getModificationStamp();
    Project project = file.getProject();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed() || editor.isDisposed() || editor.getDocument().getModificationStamp() != stamp) return;
      //no need to optimize imports on the fly during undo/redo
      if (UndoManager.getInstance(project).isUndoOrRedoInProgress()) return;
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      String beforeText = file.getText();
      final long oldStamp = editor.getDocument().getModificationStamp();
      DocumentUtil.writeInRunUndoTransparentAction(runnable);
      if (oldStamp != editor.getDocument().getModificationStamp()) {
        String afterText = file.getText();
        if (Comparing.strEqual(beforeText, afterText)) {
          String path = file.getViewProvider().getVirtualFile().getPath();
          LOG.error("Import optimizer  hasn't optimized any imports", new Attachment(path, afterText));
        }
      }
    });
  }
}
