// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Allows passing additional information about Table renderer to the UI Inspector.
 * <p>
 * Can be implemented by {@link javax.swing.table.TableCellRenderer}.
 *
 * @see UiInspectorContextProvider
 */
public interface UiInspectorTableRendererContextProvider {
  @NotNull
  List<PropertyBean> getUiInspectorContext(@NotNull JTable tree, @Nullable Object value, int row, int column);
}
