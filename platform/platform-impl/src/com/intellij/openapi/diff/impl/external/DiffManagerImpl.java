// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.vcs.changes.actions.migrate.MigrateDiffTool;

@Deprecated
public class DiffManagerImpl extends DiffManager {
  private static final MarkupEditorFilter DIFF_EDITOR_FILTER = editor -> DiffUtil.isDiffEditor(editor);

  @Override
  public DiffTool getIdeaDiffTool() {
    return MigrateDiffTool.INSTANCE;
  }

  @Override
  public DiffTool getDiffTool() {
    return MigrateDiffTool.INSTANCE;
  }

  @Override
  public MarkupEditorFilter getDiffEditorFilter() {
    return DIFF_EDITOR_FILTER;
  }
}
