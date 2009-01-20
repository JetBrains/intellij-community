package org.jetbrains.plugins.groovy.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.structure.elements.impl.GroovyFileStructureViewElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */

public class GroovyStructureViewModel extends TextEditorBasedStructureViewModel {
  private final GroovyFileBase myRootElement;

  private static final Class[] SUITABLE_CLASSES =
    new Class[]{GroovyFileBase.class, GrTypeDefinition.class, GrMethod.class, GrVariable.class};

  public GroovyStructureViewModel(GroovyFileBase rootElement) {
    super(rootElement);
    myRootElement = rootElement;
  }

  protected PsiFile getPsiFile() {
    return myRootElement;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new GroovyFileStructureViewElement(myRootElement);
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
    return new Filter[]{new GroovyInheritFilter()};
  }

  @Override
  public boolean shouldEnterElement(Object element) {
    return element instanceof GrTypeDefinition;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return SUITABLE_CLASSES;
  }
}
