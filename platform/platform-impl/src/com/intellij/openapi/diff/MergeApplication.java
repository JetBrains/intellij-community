/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated
public class MergeApplication extends ApplicationStarterBase {
  public MergeApplication() {
    super("merge", 3 ,4);
  }

  @Override
  public String getUsageMessage() {
    final String script = ApplicationNamesInfo.getInstance().getScriptName();
    return String.format("Usage:\n\t%s merge <file1> <file2> <original>\n\t%s merge <file1> <file2> <original> <output>", script, script);
  }

  @Override
  protected void processCommand(String[] args, @Nullable String currentDirectory) throws Exception {
    final VirtualFile left = findFile(args[1], currentDirectory);
    final VirtualFile right = findFile(args[2], currentDirectory);
    final VirtualFile middle = findFile(args[3], currentDirectory);
    final VirtualFile result = findOrCreateFile(args.length == 4 ? args[3] : args[4], currentDirectory);

    MergeRequest request = DiffRequestFactory.getInstance()
      .createMergeRequest(getText(left), getText(right), getText(middle), result,
                          ProjectManager.getInstance().getDefaultProject(),
                          ActionButtonPresentation.APPLY,
                          ActionButtonPresentation.CANCEL_WITH_PROMPT);
    request.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
    request.setWindowTitle("Merge");
    request.setVersionTitles(new String[]{left.getPresentableUrl(), result.getPresentableUrl(), middle.getPresentableUrl()});
    DiffManager.getInstance().getDiffTool().show(request);
  }
}
