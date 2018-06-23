// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

import javax.swing.*;

public class TestDataGroupFileIconProvider implements FileIconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
    if (file instanceof TestDataGroupVirtualFile) {
      return AllIcons.Nodes.TestSourceFolder;
    }
    return null;
  }
}
