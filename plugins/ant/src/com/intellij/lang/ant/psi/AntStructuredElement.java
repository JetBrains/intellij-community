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

  /**
   * Finds psi file by specified name in the directory of current ant file.
   *
   * @param name    - name of the file to find.
   * @return psi file if it exists, else null.
   */
  @Nullable
  PsiFile findFileByName(final String name);

  /**
   * Finds psi file by specified name and basedir.
   *
   * @param name    - name of the file to find.
   * @param baseDir - base directory where to find the file. If the parameter is specified as null, ant project's base directory property is used.
   * @return psi file if it exists, else null.
   */
  @Nullable
  PsiFile findFileByName(final String name, @Nullable final String baseDir);

  @Nullable
  String computeAttributeValue(String value);

  boolean hasNameElement();

  boolean hasIdElement();

  @NonNls
  @Nullable
  String getFileReferenceAttribute();

  /**
   * @return true if is instance of a type defined by the <typedef> or <taskdef> task.
   */
  boolean isTypeDefined();

  /**
   * @return true if is instance of a type defined by the <presetdef> task.
   */
  boolean isPresetDefined();
}
