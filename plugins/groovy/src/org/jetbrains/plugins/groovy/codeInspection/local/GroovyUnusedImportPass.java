/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.ActionRunner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionData;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author ilyas
 */
public class GroovyUnusedImportPass extends TextEditorHighlightingPass {
  private static PsiFile myFile;
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPass");
  private AnnotationHolderImpl myAnnotationHolder = new AnnotationHolderImpl();
  private ArrayList<GrImportStatement> myUnusedImports = new ArrayList<GrImportStatement>();
  private Editor myEditor;

  public GroovyUnusedImportPass(PsiFile file, Editor editor) {
    super(file.getProject(), editor.getDocument());
    myFile = file;
    myEditor = editor;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    if (!(myFile instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) myFile;
    GroovyInspectionData inspectionData = GroovyInspectionData.getInstance(groovyFile.getProject());
    GrImportStatement[] usedImports = inspectionData.getUsedImportStatements(groovyFile);
    HashSet<GrImportStatement> allImports = new HashSet<GrImportStatement>(Arrays.asList(groovyFile.getImportStatements()));
    boolean changed = allImports.removeAll(Arrays.asList(usedImports));
    if (allImports.size() > 0 && (usedImports.length == 0 || changed)) {
      myUnusedImports.addAll(allImports);
    }
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
    for (GrImportStatement unusedImport : myUnusedImports) {
      GrCodeReferenceElement importReference = unusedImport.getImportReference();
      if (importReference != null && importReference.resolve() != null) {
        IntentionAction action = createUnusedImportIntention();
        Annotation annotation = myAnnotationHolder.createWarningAnnotation(unusedImport, GroovyInspectionBundle.message("unused.import"));
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        annotation.registerFix(action);
      }
    }

    HighlightInfoHolder holder = new HighlightInfoHolder(myFile, HighlightInfoFilter.EMPTY_ARRAY);
    holder.setWritable(true);
    for (Annotation annotation : myAnnotationHolder) {
      holder.add(HighlightUtil.convertToHighlightInfo(annotation));
    }

    for (HighlightInfo info : holder) {
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, info.startOffset, info.endOffset, holder, Pass.EXTERNAL_TOOLS);
    }
  }
}
