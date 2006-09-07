package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
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
  PsiFile findFileByName(final String name, final boolean ignoreBasedir);

  @Nullable
  String computeAttributeValue(String value);

  boolean hasNameElement();

  boolean hasIdElement();

  @NonNls @Nullable
  String getFileReferenceAttribute();

  /**
  /* Returns true if is instance of a type defined by the <typedef> or <taskdef> task.
  */
  boolean isTypeDefined();

  /**
  /* Returns true if is instance of a type defined by the <presetdef> task.
  */
  boolean isPresetDefined();
}
