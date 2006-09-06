package com.intellij.lang.ant.psi.usages;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntUsagesProvider implements FindUsagesProvider {

  @Nullable
  public WordsScanner getWordsScanner() {
    return null;
  }

  public boolean canFindUsagesFor(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) return false;
    AntStructuredElement se = (AntStructuredElement)element;
    return se.hasNameElement() || se.hasIdElement();
  }

  @Nullable
  public String getHelpId(PsiElement element) {
    return null;
  }

  @NotNull
  public String getType(PsiElement element) {
    return ((AntStructuredElement)element).getSourceElement().getName();
  }

  @NotNull
  public String getDescriptiveName(PsiElement element) {
    if( element instanceof AntFile) {
      return ((AntFile)element).getName();
    }
    final AntStructuredElement se = (AntStructuredElement)element;
    final String name = se.getName();
    if (name != null) return name;
    return se.getSourceElement().getName();
  }

  @NotNull
  public String getNodeText(PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
