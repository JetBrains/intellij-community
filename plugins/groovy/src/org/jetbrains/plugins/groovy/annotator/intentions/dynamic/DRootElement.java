package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.11.2007
 */
public class DRootElement implements DElement {
  public Map<String, DClassElement> containingClasses = new HashMap<String, DClassElement>();

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

  public void setContainingClasses(Map<String, DClassElement> containingClasses) {
    this.containingClasses = containingClasses;
  }

  public DClassElement removeClassElement(String classElementName) {
    return containingClasses.remove(classElementName);
  }
}