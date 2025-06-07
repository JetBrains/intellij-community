// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

  public @Nullable DClassElement getClassElement(String name) {
    return containingClasses.get(name);
  }

  public Collection<DClassElement> getContainingClasses() {
    return containingClasses.values();
  }

  public DClassElement removeClassElement(String classElementName) {
    return containingClasses.remove(classElementName);
  }
}