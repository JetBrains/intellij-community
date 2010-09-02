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

package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyImportsTracker;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyUnusedImportPass extends TextEditorHighlightingPass {
  private final GroovyFile myFile;
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPass");
  private volatile Set<GrImportStatement> myUnusedImports = Collections.emptySet();
  private volatile Runnable myOptimizeRunnable;

  public GroovyUnusedImportPass(GroovyFile file, Editor editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    GroovyImportsTracker importsTracker = GroovyImportsTracker.getInstance(myFile.getProject());
    myUnusedImports = importsTracker.getUnusedImportStatements(myFile);
    if (!myUnusedImports.isEmpty() && CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
      myOptimizeRunnable = new GroovyImportOptimizer().processFile(myFile);
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

      public void invoke(@NotNull final Project project, Editor editor, PsiFile file) {
        GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
        final Runnable runnable = optimizer.processFile(file);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(project, runnable, "optimize imports", this);
          }
        });
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  public void doApplyInformationToEditor() {
    AnnotationHolder annotationHolder = new AnnotationHolderImpl();
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>(myUnusedImports.size());
    for (GrImportStatement unusedImport : myUnusedImports) {
      Annotation annotation = annotationHolder.createWarningAnnotation(unusedImport, GroovyInspectionBundle.message("unused.import"));
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      annotation.registerFix(createUnusedImportIntention());
      infos.add(HighlightInfo.fromAnnotation(annotation));
    }

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getId());

    final Runnable optimize = myOptimizeRunnable;
    if (optimize != null && timeToOptimizeImports()) {
      PostHighlightingPass.invokeOnTheFlyImportOptimizer(new Runnable() {
        @Override
        public void run() {
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          optimize.run();
        }
      });
    }
  }

  private boolean timeToOptimizeImports() {
    if (!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) return false;

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    if (!codeAnalyzer.isHighlightingAvailable(myFile)) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(myFile)) return false;
    boolean errors = containsErrorsPreventingOptimize();

    return !errors && codeAnalyzer.canChangeFileSilently(myFile);
  }

  private boolean containsErrorsPreventingOptimize() {
    // ignore unresolved imports errors
    final TextRange ignoreRange;
    final GrImportStatement[] imports = myFile.getImportStatements();
    if (imports.length != 0) {
      final int start = imports[0].getTextRange().getStartOffset();
      final int end = imports[imports.length - 1].getTextRange().getEndOffset();
      ignoreRange = new TextRange(start, end);
    } else {
      ignoreRange = new TextRange(0, 0);
    }

    boolean hasErrorsExceptUnresolvedImports = !DaemonCodeAnalyzerImpl.processHighlights(myDocument, myProject, HighlightSeverity.ERROR, 0, myDocument.getTextLength(), new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo error) {
        int infoStart = error.getActualStartOffset();
        int infoEnd = error.getActualEndOffset();

        return ignoreRange.containsRange(infoStart,infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
      }
    });
    return hasErrorsExceptUnresolvedImports;
  }


}
