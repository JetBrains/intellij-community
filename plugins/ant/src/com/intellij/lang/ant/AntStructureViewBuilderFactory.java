/*
 * @author max
 */
package com.intellij.lang.ant;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.lang.ant.config.impl.configuration.AntStructureViewTreeModel;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntStructureViewBuilderFactory implements PsiStructureViewFactory {
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
    final AntFile antFile = AntSupport.getAntFile(psiFile);
    if (antFile != null ) {
      return new TreeBasedStructureViewBuilder() {
        @NotNull
        public StructureViewModel createStructureViewModel() {
          return new AntStructureViewTreeModel(antFile);
        }
      };
    }
    return null;
  }

}