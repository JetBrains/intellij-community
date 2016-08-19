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

import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class StructureViewModelWrapper implements StructureViewModel {
  private final StructureViewModel myStructureViewModel;
  private final PsiFile myMainFile;

  public StructureViewModelWrapper(StructureViewModel structureViewModel, PsiFile mainFile) {
    myStructureViewModel = structureViewModel;
    myMainFile = mainFile;
  }

  @Override
  public Object getCurrentEditorElement() {
    return myStructureViewModel.getCurrentEditorElement();
  }

  @Override
  public void addEditorPositionListener(@NotNull final FileEditorPositionListener listener) {
    myStructureViewModel.addEditorPositionListener(listener);
  }

  @Override
  public void removeEditorPositionListener(@NotNull final FileEditorPositionListener listener) {
    myStructureViewModel.removeEditorPositionListener(listener);
  }

  @Override
  public void addModelListener(@NotNull final ModelListener modelListener) {
    myStructureViewModel.addModelListener(modelListener);
  }

  @Override
  public void removeModelListener(@NotNull final ModelListener modelListener) {
    myStructureViewModel.removeModelListener(modelListener);
  }

  @Override
  @NotNull
  public StructureViewTreeElement getRoot() {
    return new StructureViewElementWrapper<>(myStructureViewModel.getRoot(), myMainFile);
  }

  @Override
  public void dispose() {
    myStructureViewModel.dispose();
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  @Override
  @NotNull
  public Grouper[] getGroupers() {
    return myStructureViewModel.getGroupers();
  }

  @Override
  @NotNull
  public Sorter[] getSorters() {
    return myStructureViewModel.getSorters();
  }

  @Override
  @NotNull
  public Filter[] getFilters() {
    return myStructureViewModel.getFilters();
  }
}
