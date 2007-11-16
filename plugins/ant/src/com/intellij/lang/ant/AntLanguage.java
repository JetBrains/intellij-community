package com.intellij.lang.ant;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.config.impl.configuration.AntStructureViewTreeModel;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class AntLanguage extends Language {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.AntLanguage");

  public AntLanguage() {
    super("ANT");
    StdLanguages.ANT = this;
  }

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
