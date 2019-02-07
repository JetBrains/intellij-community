// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.CodeSmellDetector;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The check-in handler which performs code analysis before check-in. Source code for this class
 * is provided as a sample of using the {@link CheckinHandler} API.
 *
 * @author lesya
 * @since 5.1
 */
public class CodeAnalysisBeforeCheckinHandler extends CheckinHandler {

  private final Project myProject;
  private final CheckinProjectPanel myCheckinPanel;
  private static final Logger LOG = Logger.getInstance(CodeAnalysisBeforeCheckinHandler.class);

  public CodeAnalysisBeforeCheckinHandler(final Project project, CheckinProjectPanel panel) {
    myProject = project;
    myCheckinPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    return new BooleanCommitOption(myCheckinPanel, VcsBundle.message("before.checkin.standard.options.check.smells"), true,
                                   () -> getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT,
                                   value -> getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = value);
  }

  private VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  private ReturnResult processFoundCodeSmells(final List<CodeSmellInfo> codeSmells, @Nullable CommitExecutor executor) {
    int errorCount = collectErrors(codeSmells);
    int warningCount = codeSmells.size() - errorCount;
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Set<VirtualFile> virtualFiles = ContainerUtil.map2Set(codeSmells, (smell) -> fileDocumentManager.getFile(smell.getDocument()));
    String commitButtonText = executor != null ? executor.getActionText() : myCheckinPanel.getCommitActionName();
    commitButtonText = StringUtil.trimEnd(commitButtonText, "...");

    String message = virtualFiles.size() == 1
      ? VcsBundle.message("before.commit.file.contains.code.smells.edit.them.confirm.text",
                          FileUtil.toSystemDependentName(FileUtil.getLocationRelativeToUserHome(virtualFiles.iterator().next().getPath())), errorCount, warningCount)
      : VcsBundle.message("before.commit.files.contain.code.smells.edit.them.confirm.text",
                          virtualFiles.size(), errorCount, warningCount);

    final int answer = Messages.showYesNoCancelDialog(myProject,
                                                      message,
                                                      VcsBundle.message("code.smells.error.messages.tab.name"),
                                                      VcsBundle.message("code.smells.review.button"),
                                                      commitButtonText, CommonBundle.getCancelButtonText(), UIUtil.getWarningIcon());
    if (answer == Messages.YES) {
      CodeSmellDetector.getInstance(myProject).showCodeSmellErrors(codeSmells);
      return ReturnResult.CLOSE_WINDOW;
    }
    if (answer == Messages.CANCEL) {
      return ReturnResult.CANCEL;
    }
    return ReturnResult.COMMIT;
  }

  private static int collectErrors(final List<CodeSmellInfo> codeSmells) {
    int result = 0;
    for (CodeSmellInfo codeSmellInfo : codeSmells) {
      if (codeSmellInfo.getSeverity() == HighlightSeverity.ERROR) result++;
    }
    return result;
  }

  @Override
  public ReturnResult beforeCheckin(CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    if (getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT) {
      if (DumbService.getInstance(myProject).isDumb()) {
        if (Messages.showOkCancelDialog(myProject, VcsBundle.message("code.smells.error.indexing.message",
                                                                     ApplicationNamesInfo.getInstance().getProductName()),
                                        VcsBundle.message("code.smells.error.indexing"),
                                        "&Wait", "&Commit", null) == Messages.OK) {
          return ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }

      try {
        return runCodeAnalysis(executor);
      }
      catch (ProcessCanceledException e) {
        return ReturnResult.CANCEL;
      } catch (Exception e) {
        LOG.error(e);
        if (Messages.showOkCancelDialog(myProject,
                                        "Code analysis failed with exception: " + e.getClass().getName() + ": " + e.getMessage(),
                                        "Code Analysis Failed", "&Commit", "Ca&ncel", null) == Messages.OK) {
          return ReturnResult.COMMIT;
        }
        return ReturnResult.CANCEL;
      }
    }
    else {
      return ReturnResult.COMMIT;
    }
  }

  @NotNull
  private ReturnResult runCodeAnalysis(@Nullable CommitExecutor commitExecutor) {
    final List<VirtualFile> files = CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles(myCheckinPanel.getVirtualFiles(), myProject);
    if (files.size() <= Registry.intValue("vcs.code.analysis.before.checkin.show.only.new.threshold", 0)) {
      return runCodeAnalysisNew(commitExecutor, files);
    }
    return runCodeAnalysisOld(commitExecutor, files);
  }

  @NotNull
  private ReturnResult runCodeAnalysisNew(@Nullable CommitExecutor commitExecutor,
                                          @NotNull List<? extends VirtualFile> files) {
    Ref<List<CodeSmellInfo>> codeSmells = Ref.create();
    Ref<Exception> exception = Ref.create();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    ProgressManager.getInstance().run(new Task.Modal(myProject, VcsBundle.message("checking.code.smells.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          assert myProject != null;
          indicator.setIndeterminate(true);
          codeSmells.set(CodeAnalysisBeforeCheckinShowOnlyNew.runAnalysis(myProject, files, indicator));
          indicator.setText(VcsBundle.getString("before.checkin.waiting.for.smart.mode"));
          DumbService.getInstance(myProject).waitForSmartMode();
        } catch (ProcessCanceledException e) {
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
    if (!codeSmells.get().isEmpty()) {
      return processFoundCodeSmells(codeSmells.get(), commitExecutor);
    }
    return ReturnResult.COMMIT;
  }

  @NotNull
  private ReturnResult runCodeAnalysisOld(@Nullable CommitExecutor commitExecutor,
                                          @NotNull List<? extends VirtualFile> files) {
    final List<CodeSmellInfo> codeSmells = CodeSmellDetector.getInstance(myProject).findCodeSmells(files);
    if (!codeSmells.isEmpty()) {
      return processFoundCodeSmells(codeSmells, commitExecutor);
    }
    return ReturnResult.COMMIT;
  }
}
