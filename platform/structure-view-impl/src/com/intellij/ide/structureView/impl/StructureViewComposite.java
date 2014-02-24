/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author cdr
 */
public class StructureViewComposite implements StructureView {
  @NotNull private final StructureViewDescriptor[] myStructureViews;
  @NotNull private final StructureViewDescriptor mySelectedViewDescriptor;
  public static class StructureViewDescriptor {
    public final String title;
    public final StructureView structureView;
    public final Icon icon;

    public StructureViewDescriptor(final String title, @NotNull StructureView structureView, Icon icon) {
      this.title = title;
      this.structureView = structureView;
      this.icon = icon;
    }
  }

  public StructureViewComposite(@NotNull StructureViewDescriptor... views) {
    myStructureViews = views;
    for (StructureViewDescriptor descriptor : views) {
      Disposer.register(this, descriptor.structureView);
    }
    mySelectedViewDescriptor = views[0];
  }

  public StructureView getSelectedStructureView() {
    return mySelectedViewDescriptor.structureView;
  }

  public void setStructureView(int index, StructureViewDescriptor view) {
    myStructureViews[index] = view;
    Disposer.register(this, view.structureView);
  }

  @Override
  public FileEditor getFileEditor() {
    return getSelectedStructureView().getFileEditor();
  }

  @Override
  public boolean navigateToSelectedElement(final boolean requestFocus) {
    return getSelectedStructureView().navigateToSelectedElement(requestFocus);
  }

  @Override
  public JComponent getComponent() {
    return mySelectedViewDescriptor.structureView.getComponent();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void centerSelectedRow() {
    getSelectedStructureView().centerSelectedRow();
  }

  @Override
  public void restoreState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.restoreState();
    }
  }

  @Override
  public void storeState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.storeState();
    }
  }

  @NotNull
  public StructureViewDescriptor[] getStructureViews() {
    return myStructureViews;
  }

  @Override
  @NotNull
  public StructureViewModel getTreeModel() {
    return getSelectedStructureView().getTreeModel();
  }

}
