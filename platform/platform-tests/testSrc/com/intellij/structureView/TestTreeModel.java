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
  private final List<Filter> myFilters = new ArrayList<Filter>();
  private final List<Sorter> mySorters = new ArrayList<Sorter>();

  public TestTreeModel(StringTreeElement root) {
    myRoot = root;

  }

  @Override
  @NotNull
  public StructureViewTreeElement getRoot() {
    return myRoot;
  }

  @Override
  @NotNull
  public Filter[] getFilters() {
    return myFilters.toArray(new Filter[myFilters.size()]);
  }

  @Override
  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{new TestGrouper(new String[]{"a", "b", "c"}),
                         new TestGrouper(new String[]{"d", "e", "f"})};
  }

  @Override
  @NotNull
  public Sorter[] getSorters() {
    return mySorters.toArray(new Sorter[mySorters.size()]);
  }

  public void addFlter(Filter filter) {
    myFilters.add(filter);
  }

  public void addSorter(Sorter sorter) {
    mySorters.add(sorter);
  }

  public static class StringTreeElement implements StructureViewTreeElement {
    private final Collection<StructureViewTreeElement> myChildren = new ArrayList<StructureViewTreeElement>();
    private final String myValue;

    public StringTreeElement(@NonNls String value) {
      myValue = value;
    }

    @Override
    public StructureViewTreeElement[] getChildren() {
      return myChildren.toArray(new StructureViewTreeElement[myChildren.size()]);

    }

    @Override
    public ItemPresentation getPresentation() {
      return new PresentationData(myValue, null, null, null);
    }

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

    @Override
    public void navigate(boolean requestFocus) {
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }

  @Override
  public Object getCurrentEditorElement() {
    return null;
  }

  @Override
  public void addEditorPositionListener(FileEditorPositionListener listener) {
  }

  @Override
  public void removeEditorPositionListener(FileEditorPositionListener listener) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  @Override
  public void addModelListener(ModelListener modelListener) {

  }

  @Override
  public void removeModelListener(ModelListener modelListener) {
    
  }
}
