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

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
@Deprecated
public class DiffApplication extends ApplicationStarterBase {
  public DiffApplication() {
    super("diff", 2);
  }

  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("diff.application.usage.parameters.and.description", scriptName);
  }

  public void processCommand(String[] args, @Nullable String currentDirectory) throws OperationFailedException {
    final String path1 = args[1];
    final String path2 = args[2];
    final VirtualFile file1 = findFile(path1, currentDirectory);
    final VirtualFile file2 = findFile(path2, currentDirectory);
    final boolean areDirs = areDirs(file1, file2);
    final boolean areJars = areJars(file1, file2);
    if (areDirs || areJars) {
      final DirDiffManager diffManager = DirDiffManager.getInstance(ProjectManager.getInstance().getDefaultProject());
      final DiffElement d1 = diffManager.createDiffElement(file1);
      final DiffElement d2 = diffManager.createDiffElement(file2);
      if (d1 == null) {
        throw new OperationFailedException(DiffBundle.message("cannot.create.diff.error", path1));
      }
      if (d2 == null) {
        throw new OperationFailedException(DiffBundle.message("cannot.create.diff.error", path1));
      }
      else if (!diffManager.canShow(d1, d2)) {
        throw new OperationFailedException(DiffBundle.message("cannot.compare.error", path1, path2));
      }

      final DirDiffSettings settings = new DirDiffSettings();
      settings.showInFrame = false;
      diffManager.showDiff(d1, d2, settings, null);
    }
    else {
      file1.refresh(false, false);
      file2.refresh(false, false);

      if (file1.getFileType() == UnknownFileType.INSTANCE) {
        throw new OperationFailedException(DiffBundle.message("unknown.file.type.error", path1));
      }
      else if (file2.getFileType() == UnknownFileType.INSTANCE) {
        throw new OperationFailedException(DiffBundle.message("unknown.file.type.error", path2));
      }

      SimpleDiffRequest request = SimpleDiffRequest.compareFiles(file1, file2, ProjectManager.getInstance().getDefaultProject());
      request.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
      DiffManager.getInstance().getIdeaDiffTool().show(request);
    }
  }
}
