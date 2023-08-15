// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class Hyperlink {

  private final @Nullable Icon myIcon;
  private final @NotNull @NlsContexts.LinkLabel String linkText;
  private final @NotNull SimpleTextAttributes textAttributes;

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

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @NotNull @NlsContexts.LinkLabel String getLinkText() {
    return linkText;
  }

  public @NotNull SimpleTextAttributes getTextAttributes() {
    return textAttributes;
  }

  public abstract void onClick(MouseEvent event);
}
