package org.jetbrains.javafx.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.JavaFxFunctionDefinition;
import org.jetbrains.javafx.lang.psi.JavaFxVariableDeclaration;

/**
 * Structure view support
 *
 * @author Alexey.Ivanov
 */
public class JavaFxStructureViewFactory implements PsiStructureViewFactory {
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new StructureViewModelBase(psiFile, new JavaFxStructureViewElement((JavaFxElement)psiFile)).withSorters(Sorter.ALPHA_SORTER)
          .withSuitableClasses(JavaFxClassDefinition.class, JavaFxFunctionDefinition.class, JavaFxVariableDeclaration.class);
      }
    };
  }
}
