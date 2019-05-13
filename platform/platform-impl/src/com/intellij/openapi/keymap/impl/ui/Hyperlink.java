/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public abstract class Hyperlink {

  @Nullable
  private final Icon myIcon;
  @NotNull
  private final String linkText;
  @NotNull
  private final SimpleTextAttributes textAttributes;

  protected Hyperlink(@NotNull String linkText) {
    this(null, linkText, SimpleTextAttributes.LINK_ATTRIBUTES);
  }

  protected Hyperlink(@Nullable Icon icon, @NotNull String linkText) {
    this(icon, linkText, SimpleTextAttributes.LINK_ATTRIBUTES);
  }

  protected Hyperlink(@Nullable Icon icon, @NotNull String linkText, @NotNull SimpleTextAttributes textAttributes) {
    myIcon = icon;
    this.linkText = linkText;
    this.textAttributes = textAttributes;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getLinkText() {
    return linkText;
  }

  @NotNull
  public SimpleTextAttributes getTextAttributes() {
    return textAttributes;
  }

  public abstract void onClick(MouseEvent event);
}
