/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CodeSmellDetector;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class CodeSmellDetectorImpl extends CodeSmellDetector {
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.CodeSmellDetectorImpl");

  public CodeSmellDetectorImpl(final Project project) {
    myProject = project;
  }

  @Override
  public void showCodeSmellErrors(@NotNull final List<CodeSmellInfo> smellList) {
    Collections.sort(smellList, new Comparator<CodeSmellInfo>() {
      @Override
      public int compare(final CodeSmellInfo o1, final CodeSmellInfo o2) {
        return o1.getTextRange().getStartOffset() - o2.getTextRange().getStartOffset();
      }
    });

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        if (smellList.isEmpty()) {
          return;
        }

        final VcsErrorViewPanel errorTreeView = new VcsErrorViewPanel(myProject);
        AbstractVcsHelperImpl helper = (AbstractVcsHelperImpl)AbstractVcsHelper.getInstance(myProject);
        helper.openMessagesView(errorTreeView, VcsBundle.message("code.smells.error.messages.tab.name"));

        FileDocumentManager fileManager = FileDocumentManager.getInstance();

        for (CodeSmellInfo smellInfo : smellList) {
          final VirtualFile file = fileManager.getFile(smellInfo.getDocument());
          final OpenFileDescriptor navigatable =
            new OpenFileDescriptor(myProject, file, smellInfo.getStartLine(), smellInfo.getStartColumn());
          final String exportPrefix = NewErrorTreeViewPanel.createExportPrefix(smellInfo.getStartLine() + 1);
          final String rendererPrefix =
            NewErrorTreeViewPanel.createRendererPrefix(smellInfo.getStartLine() + 1, smellInfo.getStartColumn() + 1);
          if (smellInfo.getSeverity() == HighlightSeverity.ERROR) {
            errorTreeView.addMessage(MessageCategory.ERROR, new String[]{smellInfo.getDescription()}, file.getPresentableUrl(), navigatable,
                                     exportPrefix, rendererPrefix, null);
          }
          else {//if (smellInfo.getSeverity() == HighlightSeverity.WARNING) {
            errorTreeView.addMessage(MessageCategory.WARNING, new String[]{smellInfo.getDescription()}, file.getPresentableUrl(),
                                     navigatable, exportPrefix, rendererPrefix, null);
          }

        }
      }
    });

  }

  @NotNull
  @Override
  public List<CodeSmellInfo> findCodeSmells(@NotNull final List<VirtualFile> filesToCheck) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final List<CodeSmellInfo> result = new ArrayList<>();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) throw new RuntimeException("Must not run under write action");

    final Ref<Exception> exception = Ref.create();
    ProgressManager.getInstance().run(new Task.Modal(myProject, VcsBundle.message("checking.code.smells.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator progress) {
        try {
          for (int i = 0; i < filesToCheck.size(); i++) {
            if (progress.isCanceled()) throw new ProcessCanceledException();

            final VirtualFile file = filesToCheck.get(i);

            progress.setText(VcsBundle.message("searching.for.code.smells.processing.file.progress.text", file.getPresentableUrl()));
            progress.setFraction((double)i / (double)filesToCheck.size());

            result.addAll(findCodeSmells(file, progress));
          }
        }
        catch (ProcessCanceledException e) {
          exception.set(e);
        }
        catch (Exception e) {
          LOG.error(e);
          exception.set(e);
        }
      }
    });
    if (!exception.isNull()) {
      ExceptionUtil.rethrowAllAsUnchecked(exception.get());
    }

    return result;
  }

  @NotNull
  private List<CodeSmellInfo> findCodeSmells(@NotNull final VirtualFile file, @NotNull final ProgressIndicator progress) {
    final List<CodeSmellInfo> result = Collections.synchronizedList(new ArrayList<CodeSmellInfo>());

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    final ProgressIndicator daemonIndicator = new DaemonProgressIndicator();
    ((ProgressIndicatorEx)progress).addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public void cancel() {
        super.cancel();
        daemonIndicator.cancel();
      }
    });
    ProgressManager.getInstance().runProcess(new Runnable() {
      @Override
      public void run() {
        DumbService.getInstance(myProject).runReadActionInSmartMode(new Runnable() {
          @Override
          public void run() {
            final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
            final Document document = FileDocumentManager.getInstance().getDocument(file);
            if (psiFile == null || document == null) {
              return;
            }
            List<HighlightInfo> infos = codeAnalyzer.runMainPasses(psiFile, document, daemonIndicator);
            convertErrorsAndWarnings(infos, result, document);
          }
        });
      }
    }, daemonIndicator);

    return result;
  }

  private void convertErrorsAndWarnings(@NotNull Collection<HighlightInfo> highlights,
                                        @NotNull List<CodeSmellInfo> result,
                                        @NotNull Document document) {
    for (HighlightInfo highlightInfo : highlights) {
      final HighlightSeverity severity = highlightInfo.getSeverity();
      if (SeverityRegistrar.getSeverityRegistrar(myProject).compare(severity, HighlightSeverity.WARNING) >= 0) {
        result.add(new CodeSmellInfo(document, getDescription(highlightInfo),
                                     new TextRange(highlightInfo.startOffset, highlightInfo.endOffset), severity));
      }
    }
  }

  private static String getDescription(@NotNull HighlightInfo highlightInfo) {
    final String description = highlightInfo.getDescription();
    final HighlightInfoType type = highlightInfo.type;
    if (type instanceof HighlightInfoType.HighlightInfoTypeSeverityByKey) {
      final HighlightDisplayKey severityKey = ((HighlightInfoType.HighlightInfoTypeSeverityByKey)type).getSeverityKey();
      final String id = severityKey.getID();
      return "[" + id + "] " + description;
    }
    return description;
  }


}
