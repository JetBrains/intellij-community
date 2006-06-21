package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntStructuredElement extends AntElement {

  @NotNull
  XmlTag getSourceElement();

  @Nullable
  AntTypeDefinition getTypeDefinition();

  void registerCustomType(final AntTypeDefinition def);

  void unregisterCustomType(final AntTypeDefinition def);

  boolean hasImportedTypeDefinition();

  @Nullable
  PsiFile findFileByName(final String name);

  String computeAttributeValue(String value);

  void registerRefId(final String id, AntElement element);

  @Nullable
  AntElement getElementByRefId(String refid);

  @NotNull
  String[] getRefIds();

  boolean hasNameElement();

  boolean hasIdElement();

  boolean isNameElement(PsiElement element);

  boolean isIdElement(PsiElement element);

  boolean canContainFileReference();

  boolean isPresetDefined();
}
