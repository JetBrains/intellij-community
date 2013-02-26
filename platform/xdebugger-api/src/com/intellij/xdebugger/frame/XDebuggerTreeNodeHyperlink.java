/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;
import java.awt.event.MouseEvent;

/**
 * Describes a hyperlink inside a debugger node
 */
public abstract class XDebuggerTreeNodeHyperlink {
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
    return SimpleTextAttributes.GRAY_ATTRIBUTES;
  }

  public abstract void onClick(MouseEvent event);

  public static final class HyperlinkListenerDelegator extends XDebuggerTreeNodeHyperlink {
    private final HyperlinkListener hyperlinkListener;
    private final String href;

    public HyperlinkListenerDelegator(@NotNull String linkText, @Nullable String href, @NotNull HyperlinkListener hyperlinkListener) {
      super(linkText);

      this.hyperlinkListener = hyperlinkListener;
      this.href = href;
    }

    @Override
    public void onClick(MouseEvent event) {
      hyperlinkListener.hyperlinkUpdate(IJSwingUtilities.createHyperlinkEvent(href, getLinkText()));
    }
  }
}
