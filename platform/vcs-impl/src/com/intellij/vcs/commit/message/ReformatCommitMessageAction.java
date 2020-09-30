// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.stream.Collectors.toList;

public class ReformatCommitMessageAction extends DumbAwareAction {

  public ReformatCommitMessageAction() {
    super(VcsBundle.messagePointer("commit.message.intention.family.name.reformat.commit.message"));
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Document document = getCommitMessage(e);

    e.getPresentation().setEnabled(project != null &&
                                   document != null &&
                                   getEnabledInspections(project).anyMatch(inspection -> inspection.canReformat(project, document)));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getProject());
    Document document = Objects.requireNonNull(getCommitMessage(e));

    CommandProcessor.getInstance().executeCommand(project, () ->
      WriteAction.run(() -> reformat(project, document)), VcsBundle.message("commit.message.intention.family.name.reformat.commit.message"), null);
  }

  @RequiresWriteLock
  public static void reformat(@NotNull Project project, @NotNull Document document) {
    List<BaseCommitMessageInspection> inspections = getEnabledInspections(project).collect(toList());

    inspections.forEach(inspection -> inspection.reformat(project, document));
  }

  @Nullable
  private static Document getCommitMessage(@NotNull AnActionEvent e) {
    CommitMessage commitMessage = tryCast(e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL), CommitMessage.class);
    Editor editor = commitMessage != null ? commitMessage.getEditorField().getEditor() : null;

    return editor != null ? editor.getDocument() : null;
  }

  @NotNull
  private static Stream<BaseCommitMessageInspection> getEnabledInspections(@NotNull Project project) {
    return CommitMessageInspectionProfile.getInstance(project).getAllEnabledInspectionTools(project).stream()
      .map(Tools::getTool)
      .map(InspectionToolWrapper::getTool)
      .filter(BaseCommitMessageInspection.class::isInstance)
      .map(BaseCommitMessageInspection.class::cast);
  }
}
