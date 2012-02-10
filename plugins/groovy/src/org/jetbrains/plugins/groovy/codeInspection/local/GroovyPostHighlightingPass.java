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
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyPostHighlightingPass extends TextEditorHighlightingPass {
  private final GroovyFile myFile;
  private final Editor myEditor;
  private volatile Set<GrImportStatement> myUnusedImports;
  private volatile Runnable myOptimizeRunnable;
  private volatile List<HighlightInfo> myUnusedDeclarations;

  public GroovyPostHighlightingPass(GroovyFile file, Editor editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
    myEditor = editor;
  }

  public void doCollectInformation(final ProgressIndicator progress) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    final boolean deadCodeEnabled = profile.isToolEnabled(HighlightDisplayKey.find(GroovyUnusedDeclarationInspection.SHORT_NAME), myFile);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile virtualFile = myFile.getViewProvider().getVirtualFile();
    if (!fileIndex.isInContent(virtualFile)) {
      return;
    }
    final InspectionToolWrapper wrapper = (InspectionToolWrapper)profile.getInspectionTool(UnusedDeclarationInspection.SHORT_NAME, myFile);
    final UnusedDeclarationInspection deadCodeInspection = wrapper != null ? (UnusedDeclarationInspection)wrapper.getTool() : null;
    final GlobalUsageHelper usageHelper = new GlobalUsageHelper() {
      public boolean isCurrentFileAlreadyChecked() {
        return false;
      }

      public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
        return false;
      }

      @Override
      public boolean shouldCheckUsages(@NotNull PsiMember member) {
        return deadCodeInspection == null || !deadCodeInspection.isEntryPoint(member);
      }
    };

    final List<HighlightInfo> unusedDeclarations = new ArrayList<HighlightInfo>();
    final Set<GrImportStatement> unusedImports = new HashSet<GrImportStatement>(GroovyImportOptimizer.getValidImportStatements(myFile));
    myFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof GrReferenceElement) {
          for (GroovyResolveResult result : ((GrReferenceElement)element).multiResolve(true)) {
            GroovyPsiElement context = result.getCurrentFileResolveContext();
            if (context instanceof GrImportStatement) {
              GrImportStatement importStatement = (GrImportStatement)context;
              unusedImports.remove(importStatement);
            }
          }
        }

        if (deadCodeEnabled && element instanceof GrNamedElement && !PostHighlightingPass.isImplicitUsage((GrNamedElement)element, progress)) {
          PsiElement nameId = ((GrNamedElement)element).getNameIdentifierGroovy();
          if (nameId.getNode().getElementType() == GroovyTokenTypes.mIDENT) {
            String name = ((GrNamedElement)element).getName();
            if (element instanceof GrTypeDefinition && !PostHighlightingPass.isClassUsed((GrTypeDefinition)element, progress, usageHelper)) {
              unusedDeclarations.add(
                PostHighlightingPass.createUnusedSymbolInfo(nameId, "Class " + name + " is unused", HighlightInfoType.UNUSED_SYMBOL));
            }
            else if (element instanceof GrMethod) {
              GrMethod method = (GrMethod)element;
              if (!GroovyCompletionUtil.OPERATOR_METHOD_NAMES.contains(method.getName()) &&
                  !PostHighlightingPass.isMethodReferenced(method, progress, usageHelper)) {
                unusedDeclarations.add(
                  PostHighlightingPass.createUnusedSymbolInfo(nameId, (method.isConstructor() ? "Constructor" : "Method") +" " + name + " is unused", HighlightInfoType.UNUSED_SYMBOL));
              }
            }
            else if (element instanceof GrField && PostHighlightingPass.isFieldUnused((GrField)element, progress, usageHelper)) {
              unusedDeclarations.add(
                PostHighlightingPass.createUnusedSymbolInfo(nameId, "Property " + name + " is unused", HighlightInfoType.UNUSED_SYMBOL));
            }
          }
        }
        
        super.visitElement(element);
      }
    });
    myUnusedImports = unusedImports;
    myUnusedDeclarations = unusedDeclarations;
    if (!unusedImports.isEmpty() && CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
      final VirtualFile vfile = myFile.getVirtualFile();
      if (vfile != null && ProjectRootManager.getInstance(myFile.getProject()).getFileIndex().isInSource(vfile)) {
        final GrImportStatement[] imports = myFile.getImportStatements();
        if (imports.length > 0) {
          final int offset = myEditor.getCaretModel().getOffset();
          if (imports[0].getTextRange().getStartOffset() <= offset && offset <= imports[imports.length - 1].getTextRange().getEndOffset()) {
            return;
          }
        }

        myOptimizeRunnable = new GroovyImportOptimizer().processFile(myFile);
      }
    }

  }

  private static IntentionAction createUnusedImportIntention() {
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
        optimizeImports(project, file);
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  public static void optimizeImports(final Project project, PsiFile file) {
    GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    final Runnable runnable = optimizer.processFile(file);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, runnable, "optimize imports", this);
      }
    });
  }

  public void doApplyInformationToEditor() {
    if (myUnusedDeclarations == null || myUnusedImports == null) {
      return;
    }

    AnnotationHolder annotationHolder = new AnnotationHolderImpl(new AnnotationSession(myFile));
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>(myUnusedDeclarations);
    for (GrImportStatement unusedImport : myUnusedImports) {
      Annotation annotation = annotationHolder.createWarningAnnotation(unusedImport, GroovyInspectionBundle.message("unused.import"));
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      annotation.registerFix(createUnusedImportIntention());
      infos.add(HighlightInfo.fromAnnotation(annotation));
    }

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());

    final Runnable optimize = myOptimizeRunnable;
    if (optimize != null && timeToOptimizeImports()) {
      PostHighlightingPass.invokeOnTheFlyImportOptimizer(new Runnable() {
        @Override
        public void run() {
          optimize.run();
        }
      }, myFile, myEditor);
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
      ignoreRange = TextRange.EMPTY_RANGE;
    }

    return !DaemonCodeAnalyzerImpl.processHighlights(myDocument, myProject, HighlightSeverity.ERROR, 0, myDocument.getTextLength(), new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo error) {
        int infoStart = error.getActualStartOffset();
        int infoEnd = error.getActualEndOffset();

        return ignoreRange.containsRange(infoStart,infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
      }
    });
  }


}
