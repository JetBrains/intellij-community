// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.ConvertIndentsUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class ConvertIndentsToTabsAction extends ConvertIndentsActionBase {
  @Override
  protected int performAction(Editor editor, TextRange textRange) {
    return ConvertIndentsUtil.convertIndentsToTabs(editor.getDocument(), editor.getSettings().getTabSize(editor.getProject()), textRange);
  }
}
