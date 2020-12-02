/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GradleDslNameConverter {

  boolean isGroovy();

  boolean isKotlin();

  /**
   * Converts Psi of an external language into a string suitable as input to GradleNameElement (with dotted notation indicating
   * hierarchy).  Implementors should perform syntactic analysis of the {@code element} as appropriate to the external language.
   *
   * @param element a Psi element containing the external-syntax name
   * @return a string containing a dotted-name representation of the external-named element
   */
  @NotNull
  default String psiToName(@NotNull PsiElement element) { return ""; }

  /**
   * Converts text of an external language into a string suitable as input to GradleNameElement (with dotted notation indicating
   * hierarchy).  Implementors should perform conversion of the {@code element} as appropriate to the external language.
   *
   * @param context the Dsl element in the context of which we are examining this reference
   * @param referenceText the external text denoting a reference
   * @return a string containing a dotted-name representation of the external-named element
   */
  @NotNull
  default String convertReferenceText(@NotNull GradleDslElement context, @NotNull String referenceText) {
    return "";
  }

  /**
   * Convert a reference text expressed in a dsl-compatible syntax to a text compatible with the corresponding build script language.
   * @param context the dsl context within which we are applying the conversion
   * @param referenceText the reference text.
   * @param forInjection indicates whether the reference text should be converted to be used for a variable injection or not.
   * @return reference text converted to the external language syntax.
   */
  @NotNull
  default String convertReferenceToExternalText(
    @NotNull GradleDslElement context,
    @NotNull String referenceText,
    boolean forInjection
  ) { return "";}

  /**
   * Converts a dotted-hierarchy name with hierarchy denoted by canonical model names to a dotted-hierarchy name in the vocabulary
   * of the external Dsl language.  Does not perform any syntactic transformations.
   *
   * @param modelName the canonical model namestring for this name
   * @param context the parent element of the element whose name this is
   * @return an instance of {@link ExternalNameInfo} whose {@link ExternalNameInfo#externalNameParts} field is the string containing a
   * dotted-hierarchy of external names, and whose {@link ExternalNameInfo#asMethod} field indicates whether that name is to be used as a
   * setter method (true) or in a property assignment (false), or we don't know (null).
   */
  @NotNull
  default ExternalNameInfo externalNameForParent(@NotNull String modelName, @NotNull GradleDslElement context) {
    return new ExternalNameInfo("", null);
  }

  /**
   * Converts a single external name part to a description of the Model property it is associated with.
   * Does not perform any syntactic transformations.
   *
   * @param externalName the external operator or property name
   * @param context the parent element of the element whose name this is (or will be after parsing)
   * @return a description of the model property
   */
  @Nullable
  default ModelPropertyDescription modelDescriptionForParent(@NotNull String externalName, @NotNull GradleDslElement context) { return null; }
}
