// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindowAnchor;

import javax.swing.*;

public abstract class AnchoredButton extends JToggleButton {
  protected AnchoredButton(@NlsContexts.Button String text, Icon icon, boolean selected) {
    super(text, icon, selected);
  }

  protected AnchoredButton(@NlsContexts.Button String text, Icon icon) {
    super(text, icon);
  }

  protected AnchoredButton(Action a) {
    super(a);
  }

  protected AnchoredButton(@NlsContexts.Button String text, boolean selected) {
    super(text, selected);
  }

  protected AnchoredButton(@NlsContexts.Button String text) {
    super(text);
  }

  protected AnchoredButton(Icon icon, boolean selected) {
    super(icon, selected);
  }

  protected AnchoredButton(Icon icon) {
    super(icon);
  }

  protected AnchoredButton() {
  }

  public abstract int getMnemonic2();

  public abstract ToolWindowAnchor getAnchor();
}
