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
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyImportsTracker;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyUnusedImportPass extends TextEditorHighlightingPass {
  private final PsiFile myFile;
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPass");
  private volatile Set<GrImportStatement> myUnusedImports = Collections.emptySet();

  public GroovyUnusedImportPass(PsiFile file, Editor editor) {
    super(file.getProject(), editor.getDocument(), true);
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
    Annotation[] annotations = new Annotation[myUnusedImports.size()];
    int i = 0;
    for (GrImportStatement unusedImport : myUnusedImports) {
      IntentionAction action = createUnusedImportIntention();
      Annotation annotation = annotationHolder.createWarningAnnotation(unusedImport, GroovyInspectionBundle.message("unused.import"));
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      annotation.registerFix(action);
      annotations[i++] = annotation;
    }

    List<HighlightInfo> infos = ContainerUtil.map(annotations, new Function<Annotation, HighlightInfo>() {
      public HighlightInfo fun(Annotation annotation) {
        return HighlightInfo.fromAnnotation(annotation);
      }
    });
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getId());
  }
}
