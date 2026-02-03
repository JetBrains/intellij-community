// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structureView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestTreeModel implements StructureViewModel{
  private final StringTreeElement myRoot;
  private final List<Filter> myFilters = new ArrayList<>();
  private final List<Sorter> mySorters = new ArrayList<>();

  public TestTreeModel(StringTreeElement root) {
    myRoot = root;

  }

  @Override
  @NotNull
  public StructureViewTreeElement getRoot() {
    return myRoot;
  }

  @Override
  public Filter @NotNull [] getFilters() {
    return myFilters.toArray(Filter.EMPTY_ARRAY);
  }

  @Override
  public Grouper @NotNull [] getGroupers() {
    return new Grouper[]{new TestGrouper(new String[]{"a", "b", "c"}),
                         new TestGrouper(new String[]{"d", "e", "f"})};
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return mySorters.toArray(Sorter.EMPTY_ARRAY);
  }

  public void addFlter(Filter filter) {
    myFilters.add(filter);
  }

  public void addSorter(Sorter sorter) {
    mySorters.add(sorter);
  }

  public static class StringTreeElement implements StructureViewTreeElement {
    private final Collection<StructureViewTreeElement> myChildren = new ArrayList<>();
    private final String myValue;

    public StringTreeElement(@NonNls String value) {
      myValue = value;
    }

    @Override
    public StructureViewTreeElement @NotNull [] getChildren() {
      return myChildren.toArray(StructureViewTreeElement.EMPTY_ARRAY);

    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
      return new PresentationData(myValue, null, null, null);
    }

    @Override
    public String toString() {
      return myValue;
    }

    public StringTreeElement addChild(@NonNls String s) {
      StringTreeElement child = new StringTreeElement(s);
      myChildren.add(child);
      return child;
    }

    @Override
    public String getValue() {
      return myValue;
    }
  }

  @Override
  public Object getCurrentEditorElement() {
    return null;
  }

  @Override
  public void addEditorPositionListener(@NotNull FileEditorPositionListener listener) {
  }

  @Override
  public void removeEditorPositionListener(@NotNull FileEditorPositionListener listener) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  @Override
  public void addModelListener(@NotNull ModelListener modelListener) {

  }

  @Override
  public void removeModelListener(@NotNull ModelListener modelListener) {
    
  }
}
