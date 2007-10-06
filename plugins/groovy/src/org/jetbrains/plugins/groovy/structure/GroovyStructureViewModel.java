package org.jetbrains.plugins.groovy.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */

public class GroovyStructureViewModel extends TextEditorBasedStructureViewModel {
  private final GroovyFileBase myRootElement;

  public GroovyStructureViewModel(GroovyFileBase rootElement) {
    super(rootElement);
    myRootElement = rootElement;
  }

  protected PsiFile getPsiFile() {
    return myRootElement;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new GroovyStructureViewElement(myRootElement);
  }

  @NotNull
  public Grouper[] getGroupers() {
    return Grouper.EMPTY_ARRAY;
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[]{Sorter.ALPHA_SORTER};
  }

  @NotNull
  public Filter[] getFilters() {
    return Filter.EMPTY_ARRAY;
  }
}
