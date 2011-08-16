/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class FileListRenderer extends ColoredListCellRenderer {
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    // paint selection only as a focus rectangle
    mySelected = false;
    setBackground(null);
    VirtualFile vf = (VirtualFile) value;
    setIcon(VirtualFilePresentation.getIcon(vf));
    append(vf.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    VirtualFile parent = vf.getParent();
    if (parent != null) {
      append(" (" + FileUtil.toSystemDependentName(parent.getPath()) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
