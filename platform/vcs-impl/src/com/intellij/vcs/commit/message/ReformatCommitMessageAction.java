// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ReformatCommitMessageAction extends DumbAwareAction {

  public ReformatCommitMessageAction() {
    super(VcsBundle.messagePointer("commit.message.intention.family.name.reformat.commit.message"));
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Document document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT);

    e.getPresentation().setEnabled(project != null &&
                                   document != null &&
                                   getEnabledInspections(project).anyMatch(inspection -> inspection.canReformat(project, document)));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getProject());
    Document document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT);
    if (document == null) return;

    CommandProcessor.getInstance().executeCommand(project, () ->
      WriteAction.run(() -> reformat(project, document)), VcsBundle.message("commit.message.intention.family.name.reformat.commit.message"), null);
  }

  @RequiresWriteLock
  public static void reformat(@NotNull Project project, @NotNull Document document) {
    List<BaseCommitMessageInspection> inspections = getEnabledInspections(project).toList();

    inspections.forEach(inspection -> inspection.reformat(project, document));
  }

  private static @NotNull Stream<BaseCommitMessageInspection> getEnabledInspections(@NotNull Project project) {
    return CommitMessageInspectionProfile.getInstance(project).getAllEnabledInspectionTools(project).stream()
      .map(Tools::getTool)
      .map(InspectionToolWrapper::getTool)
      .filter(BaseCommitMessageInspection.class::isInstance)
      .map(BaseCommitMessageInspection.class::cast);
  }
}
