/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.external;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ExtMergeFiles extends BaseExternalTool {
  public static final ExtMergeFiles INSTANCE = new ExtMergeFiles();

  protected ExtMergeFiles() {
    super(DiffManagerImpl.ENABLE_MERGE, DiffManagerImpl.MERGE_TOOL);
  }

  @Override
  public boolean isAvailable(@NotNull DiffRequest request) {
    if (!(request instanceof MergeRequestImpl)) return false;
    DiffContent[] contents = request.getContents();
    if (contents.length != 3) return false;
    if (externalize(request, 0) == null) return false;
    if (externalize(request, 1) == null) return false;
    if (externalize(request, 2) == null) return false;

    return true;
  }

  @Override
  @NotNull
  protected List<String> getParameters(@NotNull DiffRequest request) throws Exception {
    final List<String> params = new ArrayList<>();
    String result = ((MergeRequestImpl)request).getResultContent().getFile().getPath();
    String left = externalize(request, 0).getContentFile().getPath();
    String base = new ExternalToolContentExternalizer(request, 1).getContentFile().getPath();
    String right = externalize(request, 2).getContentFile().getPath();
    for (String param : StringUtil.split(DiffManagerImpl.MERGE_TOOL_PARAMETERS.get(getProperties()), " ")) {
      if ("%1".equals(param)) params.add(left);
      else if ("%2".equals(param)) params.add(base);
      else if ("%3".equals(param)) params.add(right);
      else if ("%4".equals(param)) params.add(result);
      else params.add(param);
    }
    return params;
  }

  @Override
  public void show(@NotNull final DiffRequest request) {
    saveContents(request);

    int result = DialogWrapper.CANCEL_EXIT_CODE;

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(getToolPath());
    try {
      commandLine.addParameters(getParameters(request));
      commandLine.createProcess();

      ProgressManager.getInstance().run(new Task.Modal(request.getProject(), "Launching external tool", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          TimeoutUtil.sleep(1000);
        }
      });

      if (Messages.YES == Messages.showYesNoDialog(request.getProject(),
                                                   "Press \"Mark as Resolved\" when you finish resolving conflicts in the external tool",
                                                   "Merge In External Tool", "Mark as Resolved", "Revert", null)) {
        result = DialogWrapper.OK_EXIT_CODE;
      }
      DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND,
                                              () -> ((MergeRequestImpl)request).getResultContent().getFile().refresh(false, false));
      // We can actually check exit code of external tool, but some of them could work with tabs -> do not close at all
    }
    catch (Exception e) {
      ExecutionErrorDialog
        .show(new ExecutionException(e.getMessage()), DiffBundle.message("cant.launch.diff.tool.error.message"), request.getProject());
    }
    finally {
      ((MergeRequestImpl)request).setResult(result);
    }
  }
}
