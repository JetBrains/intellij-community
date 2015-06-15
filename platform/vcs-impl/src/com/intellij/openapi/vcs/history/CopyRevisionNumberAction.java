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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CopyRevisionNumberAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<VcsRevisionNumber> revisions = getRevisionNumbersFromContext(e);
    revisions = ContainerUtil.reverse(revisions); // we want hashes from old to new, e.g. to be able to pass to native client in terminal
    CopyPasteManager.getInstance().setContents(new StringSelection(getHashesAsString(revisions)));
  }

  @NotNull
  private static List<VcsRevisionNumber> getRevisionNumbersFromContext(@NotNull AnActionEvent e) {
    VcsRevisionNumber[] revisionNumbers = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS);

    return revisionNumbers != null ? Arrays.asList(revisionNumbers) : Collections.<VcsRevisionNumber>emptyList();
  }

  @NotNull
  private static String getHashesAsString(@NotNull List<VcsRevisionNumber> revisions) {
    return StringUtil.join(revisions, new Function<VcsRevisionNumber, String>() {
      @Override
      public String fun(VcsRevisionNumber revision) {
        return revision.asString();
      }
    }, " ");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(!getRevisionNumbersFromContext(e).isEmpty());
  }
}
