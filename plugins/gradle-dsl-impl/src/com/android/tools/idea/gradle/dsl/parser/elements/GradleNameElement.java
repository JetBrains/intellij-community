/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleNameElement {
  /**
   * This regex is used to extract indexes out from a dereferenced map or list property.
   * Example properties would be:
   *   someListProperty[0]
   *   otherMap['key'][1]
   *   list[0][2]['key'][2]
   *
   * The first match will be the property name, with the index texts in group 1 of succeeding matches.
   * For example, for the last example property:
   *    1st match, group 0 -> "list"
   *    2nd match, group 1 -> "0"
   *    3rd match, group 1 -> "2"
   *    4th match, group 1 -> "'key'"
   *    5th match, group 1 -> "2"
   */
  @NotNull
  public static final Pattern INDEX_PATTERN = Pattern.compile("\\[(.+?)\\]|(.+?)(?=\\[)");

  @NotNull
  private static final Pattern SPACES = Pattern.compile("\\s+");

  @Nullable
  private PsiElement myNameElement;
  @Nullable
  private String myLocalName;
  @Nullable
  private String myFakeName; // Used for names that do not require a file element.
  @Nullable
  private String myName = null; // Cached version of the final name (to be reset on any change of the above fields).
  @Nullable
  private String myOriginalName; // used to detect name changes in checkForModifiedName

  /**
   * Requires read access.
   */
  @NotNull
  public static GradleNameElement from(@NotNull PsiElement element, GradleDslNameConverter converter) {
    return new GradleNameElement(element, converter);
  }

  @NotNull
  public static GradleNameElement empty() {
    return new GradleNameElement(null, null);
  }

  @NotNull
  public static GradleNameElement create(@NotNull String name) {
    return new GradleNameElement(name, false);
  }

  @NotNull
  public static GradleNameElement fake(@NotNull String name) {
    return new GradleNameElement(name, true);
  }

  @NotNull
  public static GradleNameElement copy(@NotNull GradleNameElement element) { return new GradleNameElement(element); }

  /**
   * Requires read access.
   */
  private GradleNameElement(@Nullable PsiElement element, GradleDslNameConverter converter) {
    setUpFrom(element, converter);
  }

  private GradleNameElement(@NotNull String name, boolean isFake) {
    if (isFake) {
      myFakeName = name;
    }
    else {
      myLocalName = name;
    }
    myOriginalName = name;
  }

  private GradleNameElement(@NotNull GradleNameElement element) {
    myLocalName = element.myLocalName;
    myFakeName = element.myFakeName;
    myOriginalName = element.myOriginalName;
  }

  /**
   * Changes this element to be backed by the given PsiElement. This method should not be called outside of
   * GradleWriter subclasses.
   */
  public void commitNameChange(@Nullable PsiElement nameElement,
                               GradleDslNameConverter converter,
                               GradleDslElement context) {
    setUpFrom(nameElement, converter);
    canonize(converter.modelNameForParent(fullName(), context));  // NOTYPO
  }

  @NotNull
  public String fullName() {
    List<String> parts = qualifyingParts();
    parts.add(name());
    return createNameFromParts(parts);
  }

  @NotNull
  public List<String> fullNameParts() {
    return Splitter.on(".").splitToList(fullName());
  }

  public static String createNameFromParts(@NotNull List<String> parts) {
    return String.join(".", parts);
  }

  @NotNull
  public List<String> qualifyingParts() {
    String name = findName();
    if (name == null) {
      return new ArrayList<>();
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    // Remove the last element, which is not a qualifying part;
    return nameSegments.subList(0, nameSegments.size() - 1).stream().map(GradleNameElement::convertNameToKey).collect(Collectors.toList());
  }

  public boolean isQualified() {
    String name = findName();
    if (name == null) {
      return false;
    }

    return name.contains(".");
  }

  @NotNull
  public String name() {
    String name = findName();
    if (name == null) {
      return "";
    }
    int lastDotIndex = name.lastIndexOf('.') + 1;
    return convertNameToKey(name.substring(lastDotIndex));
  }

  @Nullable
  public PsiElement getNamedPsiElement() {
    return myNameElement;
  }

  @Nullable
  public String getLocalName() {
    return myLocalName;
  }

  @Nullable
  public String getOriginalName() {
    return myOriginalName;
  }

  public void rename(@NotNull String newName) {
    if (!isFake()) {
      myLocalName = newName;
    }
    else {
      myFakeName = newName;
    }
    myName = null;
  }

  /**
   * Arranges that this element have the name given by its argument, and also that that name be considered canonical (which in practice
   * means preventing any client from detecting a difference between its current name and its original name).
   *
   * @param newName the new name to be considered canonical
   */
  public void canonize(@NotNull String newName) { // NOTYPO
    rename(newName);
    myOriginalName = newName;
  }

  public boolean isEmpty() {
    String name = findName();
    return name == null || name.isEmpty();
  }

  public boolean isFake() {
    return myNameElement == null && myFakeName != null;
  }

  @Override
  @NotNull
  public String toString() {
    return fullName();
  }

  public boolean containsPropertyReference(@NotNull String propertyReference) {
    String name = name();
    if (propertyReference.equals(name)) {
      return true;
    }

    Matcher matcher = INDEX_PATTERN.matcher(propertyReference);
    if (matcher.find() && matcher.groupCount() > 0) {
      String indexName = matcher.group(0);
      if (indexName.equals(name)) {
        return true;
      }
    }

    List<String> parts = Arrays.asList(propertyReference.split("\\."));
    if (!parts.isEmpty() && parts.get(0).equals(name)) {
      return true;
    }
    if (parts.size() > 1 && parts.get(0).equals(EXT.name) && parts.get(1).equals(name)) {
      return true;
    }

    return false;
  }

  @Nullable
  private String findName() {
    if (myName != null) return myName;
    String name = null;
    if (myLocalName != null) {
      name = myLocalName;
    }
    if (name == null && myFakeName != null) {
      name = myFakeName;
    }

    if (name != null) {
      // Remove whitespace
      name = SPACES.matcher(name).replaceAll("");
    }
    myName = name;
    return name;
  }

  @NotNull
  public static String convertNameToKey(@NotNull String str) {
    return StringUtil.unquoteString(str);
  }

  /**
   * READ ACCESS REQUIRED.
   */
  private void setUpFrom(@Nullable PsiElement element, GradleDslNameConverter converter) {
    myNameElement = element;
    if (myNameElement instanceof PsiNamedElement) {
      myLocalName = ((PsiNamedElement)myNameElement).getName();
    }
    else if (myNameElement != null) {
      myLocalName = converter.psiToName(myNameElement);
    }
    myOriginalName = myLocalName;
    myName = null;
  }
}
