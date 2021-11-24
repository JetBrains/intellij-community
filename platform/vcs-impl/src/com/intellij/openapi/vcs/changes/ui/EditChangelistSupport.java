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
 * Allows customizing create / edit change list UI.
 * And also used for customizing commit message component.
 */
public interface EditChangelistSupport {

  ExtensionPointName<EditChangelistSupport> EP_NAME = ExtensionPointName.create("com.intellij.editChangelistSupport");

  /**
   * Customizes change list name and comment components.
   * E.g. could be used to install completion to these components.
   * <p>
   * Also customizes commit message component. In this case {@code name} and {@code comment} are equal.
   *
   * @param name    change list name component or commit message component
   * @param comment change list comment component or commit message component
   */
  void installSearch(@NotNull EditorTextField name, @NotNull EditorTextField comment);

  /**
   * Allows adding custom components to create / edit change list UI.
   * And reacting to successful change list creation or edition.
   *
   * @param bottomPanel panel to add custom components
   * @param initial     change list being edited or {@code null} if new change list is created
   * @return callback to call after change list is successfully created or edited
   */
  @Nullable Consumer<@NotNull LocalChangeList> addControls(@NotNull JPanel bottomPanel, @Nullable LocalChangeList initial);

  /**
   * @deprecated Use return value of {@link #addControls(JPanel, LocalChangeList)} instead.
   */
  @Deprecated
  default void changelistCreated(@SuppressWarnings("unused") LocalChangeList changeList) {
  }
}
