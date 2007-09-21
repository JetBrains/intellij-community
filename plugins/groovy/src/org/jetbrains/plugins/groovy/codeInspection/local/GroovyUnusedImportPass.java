/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.ActionRunner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyImportsTracker;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.Collections;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyUnusedImportPass extends TextEditorHighlightingPass {
  private PsiFile myFile;
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPass");
  private volatile Set<GrImportStatement> myUnusedImports = Collections.emptySet();

  public GroovyUnusedImportPass(PsiFile file, Editor editor) {
    super(file.getProject(), editor.getDocument());
    myFile = file;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    if (!(myFile instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) myFile;
    GroovyImportsTracker importsTracker = GroovyImportsTracker.getInstance(groovyFile.getProject());
    myUnusedImports = importsTracker.getUnusedImportStatements(groovyFile);
  }

  private IntentionAction createUnusedImportIntention() {
    return new IntentionAction() {

      @NotNull
      public String getText() {
        return GroovyInspectionBundle.message("optimize.all.imports");
      }

      @NotNull
      public String getFamilyName() {
        return GroovyInspectionBundle.message("optimize.imports");
      }

      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
        final Runnable runnable = optimizer.processFile(file);
        try {
          ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
            public void run() {
              CommandProcessor.getInstance().executeCommand(project, runnable, "optimize imports", this);
            }
          });
        } catch (Exception e) {
          LOG.error(e);
        }
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  public void doApplyInformationToEditor() {
    AnnotationHolder annotationHolder = new AnnotationHolderImpl();
    Annotation[] annotations = new Annotation[myUnusedImports.size()];
    int i = 0;
    for (GrImportStatement unusedImport : myUnusedImports) {
      IntentionAction action = createUnusedImportIntention();
      Annotation annotation = annotationHolder.createWarningAnnotation(unusedImport, GroovyInspectionBundle.message("unused.import"));
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      annotation.registerFix(action);
      annotations[i++] = annotation;
    }

    HighlightInfoHolder holder = new HighlightInfoHolder(myFile, HighlightInfoFilter.EMPTY_ARRAY);
    holder.setWritable(true);
    for (Annotation annotation : annotations) {
      holder.add(HighlightUtil.convertToHighlightInfo(annotation));
    }

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), holder, getId());
  }
}
