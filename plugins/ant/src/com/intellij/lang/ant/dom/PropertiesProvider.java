/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public interface PropertiesProvider {

  // if provider implements this interface, returned property values will be treated
  // as not requiring further resolution of any property occurrences
  interface SkipPropertyExpansionInValues {}

  @NotNull
  Iterator<String> getNamesIterator();

  /**
   * @param propertyName
   * @return property value string as defined in xml or null if this provider does not define a property with such name
   */
  @Nullable
  String getPropertyValue(String propertyName);

  /**
   * Needed for referencing purposes.
   * Returned element will be used as a target element for the property reference.
   * @param propertyName
   */
  @Nullable
  PsiElement getNavigationElement(String propertyName);
}
