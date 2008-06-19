package org.jetbrains.plugins.groovy.structure;

/**
 * User: Dmitry.Krasilschikov
 * Date: 19.06.2008
 */
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

public class GroovyStructureViewFactory implements PsiStructureViewFactory {
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {

      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new GroovyStructureViewModel((GroovyFileBase) psiFile);
      }
    };
  }
}