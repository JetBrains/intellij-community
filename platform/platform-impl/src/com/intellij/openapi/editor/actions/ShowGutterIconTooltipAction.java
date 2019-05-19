// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * Shows a tooltip over a gutter icon when it's selected as an accessible element.
 * The real handler is installed from the editor gutter component.
 *
 * @author tav
 */
public class ShowGutterIconTooltipAction extends EditorAction {
  public ShowGutterIconTooltipAction() {
    super(new EditorActionHandler() {});
  }
}
