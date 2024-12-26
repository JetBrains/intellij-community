// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.copyPaste;

import com.intellij.ide.util.FQNameCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/* package */ final class KotlinImportListRenderer extends FQNameCellRenderer {
    @Override
    public @NotNull Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setIcon(null);
        return component;
    }
}
