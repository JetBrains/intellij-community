// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.ide.KillRingTransferable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Stands for emacs <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Other-Kill-Commands.html">kill-region</a> command.
 * <p/>
 * Generally, it removes currently selected text from the document and puts it to the {@link KillRingTransferable kill ring}.
 * <p/>
 * Thread-safe. 
 */
@ApiStatus.Internal
public final class KillRegionAction extends TextComponentEditorAction {

  public KillRegionAction() {
    super(new KillRingSaveAction.Handler(true));
  }
}
