// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.EditorTextField;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public interface EditChangelistSupport {

  ExtensionPointName<EditChangelistSupport> EP_NAME = ExtensionPointName.create("com.intellij.editChangelistSupport");

  void installSearch(@NotNull EditorTextField name, @NotNull EditorTextField comment);

  @Nullable Consumer<@NotNull LocalChangeList> addControls(@NotNull JPanel bottomPanel, @Nullable LocalChangeList initial);

  /**
   * @deprecated Use return value of {@link #addControls(JPanel, LocalChangeList)} instead.
   */
  @Deprecated
  default void changelistCreated(@SuppressWarnings("unused") LocalChangeList changeList) {
  }
}
