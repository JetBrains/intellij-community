// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.ConvertIndentsUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;


public class ConvertIndentsToTabsAction extends ConvertIndentsActionBase {
  @Override
  protected int performAction(Editor editor, TextRange textRange) {
    return ConvertIndentsUtil.convertIndentsToTabs(editor.getDocument(), editor.getSettings().getTabSize(editor.getProject()), textRange);
  }
}
