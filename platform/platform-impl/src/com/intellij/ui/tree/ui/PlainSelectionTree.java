// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Mark your tree with this interface to disable rounded corners in tree selection.
 * @see DefaultTreeUI#paint(Graphics, JComponent)
 * @see com.intellij.ui.components.JBTreeTable
 */
public interface PlainSelectionTree {
}
