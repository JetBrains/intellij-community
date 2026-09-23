// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** "Change Language" for files under the Scratches root; consoles and other root types keep their own actions. */
final class ScratchChangeLanguageAction extends ChangeLanguageAction {
  @Override
  protected @NotNull Condition<VirtualFile> fileFilter(@NotNull Project project) {
    return file -> !file.isDirectory() && ScratchUtil.isInScratchesRoot(file);
  }
}
