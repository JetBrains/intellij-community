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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CopySubjectAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<String> subjects = getRevisionDescriptionsFromContext(e);
    subjects = ContainerUtil.reverse(subjects); // we want subjects from old to new, just to be consistent with copy versions
    CopyPasteManager.getInstance().setContents(new StringSelection(getSubjectsAsString(subjects)));
  }

  @NotNull
  private static List<String> getRevisionDescriptionsFromContext(@NotNull AnActionEvent e) {
    String[] subjects = e.getData(VcsDataKeys.VCS_REVISION_SUBJECTS);
    return subjects != null ? Arrays.asList(subjects) : Collections.<String>emptyList();
  }

  @NotNull
  private static String getSubjectsAsString(@NotNull List<String> subjects) {
    return StringUtil.join(subjects, "\n");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(!getRevisionDescriptionsFromContext(e).isEmpty());
  }
}
