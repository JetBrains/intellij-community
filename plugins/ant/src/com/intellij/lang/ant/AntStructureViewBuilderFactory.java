/*
 * @author max
 */
package com.intellij.lang.ant;

import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AntStructureViewBuilderFactory implements PsiStructureViewFactory {
  public static final String UNNAMED = AntBundle.message("unnamed.string.presentation");

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
    final AntFile antFile = AntSupport.getAntFile(psiFile);
    if (antFile != null ) {
      return new TreeBasedStructureViewBuilder() {
        @NotNull
        public StructureViewModel createStructureViewModel() {
          return new StructureViewModelBase(antFile, new AntFileTreeElement(antFile))
            .withSuitableClasses(AntFile.class, AntTarget.class);
        }
      };
    }
    return null;
  }

  static class AntFileTreeElement extends PsiTreeElementBase<AntFile> {

    public AntFileTreeElement(AntFile antFile) {
      super(antFile);
    }

    private AntProject getAntProject() {
      return getValue().getAntProject();
    }

    public String getPresentableText() {
      final String name = getAntProject().getName();
      return name != null ? name : UNNAMED;
    }

    @NotNull
    public Collection<StructureViewTreeElement> getChildrenBase() {
      List<StructureViewTreeElement> list = new ArrayList<StructureViewTreeElement>();
      for (AntTarget target : getAntProject().getTargets()) {
        list.add(new AntTargetTreeElement(target));
      }
      Collections.sort(list, Sorter.ALPHA_SORTER.getComparator());
      return list;
    }
  }

  static class AntTargetTreeElement extends PsiTreeElementBase<AntTarget> {

    public AntTargetTreeElement(AntTarget target) {
      super(target);
    }

    public String getPresentableText() {
      final AntTarget value = getValue();
      final String name = value != null? value.getName() : null;
      return name != null ? name : UNNAMED;
    }

    @NotNull
    public Collection<StructureViewTreeElement> getChildrenBase() {
      return new ArrayList<StructureViewTreeElement>();
    }
  }
}