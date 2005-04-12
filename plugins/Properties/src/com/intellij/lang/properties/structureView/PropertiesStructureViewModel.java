package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.structureView.PropertiesStructureViewElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:13:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesStructureViewModel extends TextEditorBasedStructureViewModel {
  private PsiElement myRoot;

  public PropertiesStructureViewModel(final PsiElement root) {
    super(root.getContainingFile());
    myRoot = root;
  }

  public StructureViewTreeElement getRoot() {
    return new PropertiesStructureViewElement(myRoot);
  }

  public Grouper[] getGroupers() {
    return new Grouper[0];
  }

  public Sorter[] getSorters() {
    return new Sorter[] {Sorter.ALPHA_SORTER};
  }

  public Filter[] getFilters() {
    return new Filter[0];
  }

  protected PsiFile getPsiFile() {
    return myRoot.getContainingFile();
  }

  protected Class[] getSuitableClasses() {
    return new Class[] {Property.class};
  }
}
