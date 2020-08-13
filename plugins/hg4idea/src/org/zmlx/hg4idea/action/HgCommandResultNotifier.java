// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collections;
import java.util.List;

public final class HgCommandResultNotifier {

  private final Project myProject;

  public HgCommandResultNotifier(Project project) {
    myProject = project;
  }

  public void notifyError(@NonNls @Nullable String displayId,
                          @Nullable HgCommandResult result,
                          @NlsContexts.NotificationTitle @NotNull String failureTitle,
                          @NlsContexts.NotificationContent @NotNull String failureDescription) {
    notifyError(displayId, result, failureTitle, failureDescription, null);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public void notifyError(@NonNls @Nullable String displayId,
                          @Nullable HgCommandResult result,
                          @NlsContexts.NotificationTitle @NotNull String failureTitle,
                          @NlsContexts.NotificationContent @NotNull String failureDescription,
                          NotificationListener listener) {
    List<String> err = result != null ? result.getErrorLines() : Collections.emptyList();
    HtmlBuilder sb = new HtmlBuilder();
    if (!StringUtil.isEmptyOrSpaces(failureDescription)) {
      sb.append(failureDescription);
    }
    else {
      sb.append(failureTitle);
    }
    if (!err.isEmpty()) {
      sb.br();
      sb.appendWithSeparators(HtmlChunk.br(), ContainerUtil.map(err, HtmlChunk::text));
    }
    String errorMessage = sb.wrapWithHtmlBody().toString();

    VcsNotifier.getInstance(myProject).notifyError(displayId, failureTitle, errorMessage, listener);
  }
}
