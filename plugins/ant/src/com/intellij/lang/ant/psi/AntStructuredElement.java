/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AntStructuredElement extends AntElement, PsiNamedElement {

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
  String computeAttributeValue(@NonNls String value);

  boolean hasNameElement();

  boolean hasIdElement();

  @NonNls
  @NotNull
  List<String> getFileReferenceAttributes();

  /**
   * @return true if is instance of a type defined by the <typedef> or <taskdef> task.
   */
  boolean isTypeDefined();

  /**
   * @return true if is instance of a type defined by the <presetdef> task.
   */
  boolean isPresetDefined();
}
