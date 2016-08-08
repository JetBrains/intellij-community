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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.11.2007
 */
public class DRootElement {
  public Map<String, DClassElement> containingClasses = new HashMap<>();

  public DRootElement() {
  }

  public DClassElement mergeAddClass(DClassElement classElement) {
    final DClassElement existingClassElement = containingClasses.get(classElement.getName());

    if (existingClassElement != null) {
      final Collection<DPropertyElement> properties = existingClassElement.getProperties();
      final Set<DMethodElement> methods = existingClassElement.getMethods();

      classElement.addProperties(properties);
      classElement.addMethods(methods);
    }

    containingClasses.put(classElement.getName(), classElement);
    return classElement;
  }

  @Nullable
  public DClassElement getClassElement(String name) {
    return containingClasses.get(name);
  }

  public Collection<DClassElement> getContainingClasses() {
    return containingClasses.values();
  }

  public DClassElement removeClassElement(String classElementName) {
    return containingClasses.remove(classElementName);
  }
}