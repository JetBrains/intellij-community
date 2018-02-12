/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame;

import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Describes a hyperlink inside a debugger node
 */
public abstract class XDebuggerTreeNodeHyperlink {
  public static final SimpleTextAttributes TEXT_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0x779dbd, 0x5676a0));

  private final String linkText;

  protected XDebuggerTreeNodeHyperlink(@NotNull String linkText) {
    this.linkText = linkText;
  }

  @NotNull
  public String getLinkText() {
    return linkText;
  }

  @NotNull
  public SimpleTextAttributes getTextAttributes() {
    return TEXT_ATTRIBUTES;
  }

  public abstract void onClick(MouseEvent event);

  public boolean alwaysOnScreen() {
    return false;
  }
}
