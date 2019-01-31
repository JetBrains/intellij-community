// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
    Collections.sort(smellList, Comparator.comparingInt(o -> o.getTextRange().getStartOffset()));

    ApplicationManager.getApplication().invokeLater(() -> {
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
    });

  }

  @NotNull
  @Override
  public List<CodeSmellInfo> findCodeSmells(@NotNull final List<? extends VirtualFile> filesToCheck) throws ProcessCanceledException {
    List<CodeSmellInfo> result = new ArrayList<>();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) throw new RuntimeException("Must not run under write action");
      final Ref<Exception> exception = Ref.create();
      ProgressManager.getInstance().run(new Task.Modal(myProject, VcsBundle.message("checking.code.smells.progress.title"), true) {
        @Override
        public void run(@NotNull ProgressIndicator progress) {
          try {
            result.addAll(findCodeSmells(filesToCheck, progress));
          }
          catch (ProcessCanceledException e) {
            LOG.info("Code analysis canceled", e);
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
    }
    else if (ProgressManager.getInstance().hasProgressIndicator()) {
      result.addAll(findCodeSmells(filesToCheck, ProgressManager.getInstance().getProgressIndicator()));
    }
    else {
      throw new RuntimeException("Must run from Event Dispatch Thread or with a progress indicator");
    }

    return result;
  }

  @NotNull
  private List<CodeSmellInfo> findCodeSmells(@NotNull List<? extends VirtualFile> files,
                                             @NotNull ProgressIndicator progress) {
    final List<CodeSmellInfo> result = new ArrayList<>();
    for (int i = 0; i < files.size(); i++) {
      if (progress.isCanceled()) throw new ProcessCanceledException();

      final VirtualFile file = files.get(i);

      progress.setText(VcsBundle.message("searching.for.code.smells.processing.file.progress.text", file.getPresentableUrl()));
      progress.setFraction((double)i / (double)files.size());

      result.addAll(findCodeSmells(file, progress));
    }
    return result;
  }

  @NotNull
  private List<CodeSmellInfo> findCodeSmells(@NotNull final VirtualFile file, @NotNull final ProgressIndicator progress) {
    final ProgressIndicator daemonIndicator = new DaemonProgressIndicator();
    ((ProgressIndicatorEx)progress).addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public void cancel() {
        super.cancel();
        daemonIndicator.cancel();
      }
    });
    final PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(file));
    final Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    if (psiFile == null || document == null) {
      return Collections.emptyList();
    }

    final List<CodeSmellInfo> result = Collections.synchronizedList(new ArrayList<>());
    ProgressManager.getInstance().runProcess(() -> {
      List<HighlightInfo> infos = runMainPasses(daemonIndicator, psiFile, document);
      convertErrorsAndWarnings(infos, result, document);
    }, daemonIndicator);

    return result;
  }

  @NotNull
  private List<HighlightInfo> runMainPasses(ProgressIndicator daemonIndicator, PsiFile psiFile, Document document) {
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    ProcessCanceledException exception = null;
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    DumbService dumbService = DumbService.getInstance(myProject);
    // repeat several times when accidental background activity cancels highlighting
    int retries = 100;
    for (int i = 0; i < retries; i++) {
      int oldDelay = settings.getAutoReparseDelay();
      try {
        settings.setAutoReparseDelay(0);
        return dumbService.runReadActionInSmartMode(() -> codeAnalyzer.runMainPasses(psiFile, document, daemonIndicator));
      }
      catch (ProcessCanceledException e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getClass() != Throwable.class) {
          // canceled because of an exception, no need to repeat the same a lot times
          throw e;
        }

        exception = e;
      }
      finally {
        settings.setAutoReparseDelay(oldDelay);
      }
    }
    throw exception;
  }

  private void convertErrorsAndWarnings(@NotNull Collection<? extends HighlightInfo> highlights,
                                        @NotNull List<? super CodeSmellInfo> result,
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
