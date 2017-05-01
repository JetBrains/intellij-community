/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.commit;

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ui.CommitMessage;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.stream.Collectors.toList;

public class ReformatCommitMessageAction extends DumbAwareAction {

  public static final String NAME = "Reformat commit message";

  public ReformatCommitMessageAction() {
    super(NAME);
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
    Project project = notNull(e.getProject());
    Document document = notNull(getCommitMessage(e));

    CommandProcessor.getInstance().executeCommand(project, () ->
      WriteAction.run(() -> reformat(project, document)), NAME, null);
  }

  @CalledWithWriteLock
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
