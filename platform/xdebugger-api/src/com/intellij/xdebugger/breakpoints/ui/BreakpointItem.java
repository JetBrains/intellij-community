/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.breakpoints.ui;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.xdebugger.ui.DebuggerColors;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 5/9/12
 * Time: 4:48 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class BreakpointItem implements ItemWrapper {
  public abstract Object getBreakpoint();

  public abstract boolean isEnabled();

  public abstract void setEnabled(boolean state);

  protected void showInEditor(DetailView panel, VirtualFile virtualFile, int line) {
    panel.navigateInPreviewEditor(virtualFile, new LogicalPosition(line, 0), null);
  }

  @Override
  public void execute(Project project, JBPopup popup) {
  }

  @Override
  public void updateAccessoryView(JComponent component) {
    final JCheckBox checkBox = (JCheckBox)component;
    checkBox.setSelected(isEnabled());
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer);
  }

  @Override
  public void setupRenderer(ColoredTreeCellRenderer renderer) {
    setupGenericRenderer(renderer);
  }

  protected abstract void setupGenericRenderer(SimpleColoredComponent renderer);

}
