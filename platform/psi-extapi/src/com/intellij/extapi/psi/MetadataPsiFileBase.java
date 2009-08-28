package com.intellij.extapi.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

public abstract class MetadataPsiFileBase extends PsiFileImpl {

  @NotNull private final Language myLanguage;
  @NotNull private final ParserDefinition myParserDefinition;
  private PsiFile mySourceFile;

  public MetadataPsiFileBase(FileViewProvider provider, @NotNull Language language) {
    super(provider);
    myLanguage = language;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (parserDefinition == null) {
      throw new RuntimeException("PsiFileBase: language.getParserDefinition() returned null.");
    }
    myParserDefinition = parserDefinition;
    final IFileElementType nodeType = parserDefinition.getFileNodeType();
    init(nodeType, nodeType);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @NotNull
  public final Language getLanguage() {
    return myLanguage;
  }

  public final PsiFile getSourceFile() {
    return mySourceFile;
  }

  public final void setSourceFile(final PsiFile sourceFile) {
    mySourceFile = sourceFile;
  }
}
