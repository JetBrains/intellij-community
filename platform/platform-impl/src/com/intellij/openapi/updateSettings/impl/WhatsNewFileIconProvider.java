// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class WhatsNewFileIconProvider implements FileIconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile virtualFile, @Iconable.IconFlags int flags, @Nullable Project project) {
    Boolean isHtml = virtualFile.getUserData(HTMLEditorProvider.Companion.getHTML_CONTENT_TYPE());
    if (isHtml != null && isHtml && virtualFile.getName().startsWith(IdeBundle.message("update.whats.new.file.name", ""))) {
      return AllIcons.General.Information;
    }
    return null;
  }
}