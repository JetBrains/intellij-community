// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class Hyperlink {

  @Nullable
  private final Icon myIcon;
  @NotNull
  private final @NlsContexts.LinkLabel String linkText;
  @NotNull
  private final SimpleTextAttributes textAttributes;

  protected Hyperlink(@NotNull @NlsContexts.LinkLabel String linkText) {
    this(null, linkText, SimpleTextAttributes.LINK_ATTRIBUTES);
  }

  protected Hyperlink(@Nullable Icon icon, @NotNull @NlsContexts.LinkLabel String linkText) {
    this(icon, linkText, SimpleTextAttributes.LINK_ATTRIBUTES);
  }

  protected Hyperlink(@Nullable Icon icon, @NotNull @NlsContexts.LinkLabel String linkText, @NotNull SimpleTextAttributes textAttributes) {
    myIcon = icon;
    this.linkText = linkText;
    this.textAttributes = textAttributes;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public @NlsContexts.LinkLabel String getLinkText() {
    return linkText;
  }

  @NotNull
  public SimpleTextAttributes getTextAttributes() {
    return textAttributes;
  }

  public abstract void onClick(MouseEvent event);
}
