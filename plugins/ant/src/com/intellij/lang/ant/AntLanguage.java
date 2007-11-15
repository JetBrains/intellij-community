package com.intellij.lang.ant;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.config.impl.configuration.AntStructureViewTreeModel;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.usages.AntUsagesProvider;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntLanguage extends Language {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.AntLanguage");
  private final Language myXmlLanguage;
  private AntParserDefinition myParserDefinition;
  private AntUsagesProvider myUsagesProvider;

  public AntLanguage() {
    super("ANT");
    myXmlLanguage = StdLanguages.XML;
    LOG.assertTrue(myXmlLanguage != null, "AntLanguage should be created after XmlLanguage has created.");
    StdLanguages.ANT = this;
  }

  @Nullable
  public ParserDefinition getParserDefinition() {
    if (myParserDefinition == null) {
      myParserDefinition = new AntParserDefinition(myXmlLanguage.getParserDefinition());
    }
    return myParserDefinition;
  }

  @Nullable
  public Commenter getCommenter() {
    return myXmlLanguage.getCommenter();
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    if (myUsagesProvider == null) {
      myUsagesProvider = new AntUsagesProvider();
    }
    return myUsagesProvider;
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
