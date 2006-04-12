package com.intellij.lang.ant.psi.introspection.impl;

import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AntTaskDefinitionImpl implements AntTaskDefinition {
  private final AntProject myProject;
  private final String myName;
  private final String myNamespace;
  private final String myClassName;
  /**
   * Attribute names to their types.
   */
  private final Map<String, AntAttributeType> myAttributes;
  /**
   * Set of class names of allowed nested elements.
   */
  private final Set<String> myNestedClassNames;

  public AntTaskDefinitionImpl(AntProject project,
                               final String name,
                               final String namespace,
                               final String className,
                               final Map<String, AntAttributeType> attributes) {
    this(project, name, namespace, className, attributes, new HashSet<String>());
  }

  public AntTaskDefinitionImpl(AntProject project,
                               final String name,
                               final String namespace,
                               final String className,
                               final Map<String, AntAttributeType> attributes,
                               final Set<String> nestedElements) {
    myProject = project;
    myName = name;
    myNamespace = namespace;
    myClassName = className;
    myAttributes = attributes;
    myNestedClassNames = nestedElements;
  }

  public String getName() {
    return myName;
  }

  public String getNamespace() {
    return myNamespace;
  }

  public String getClassName() {
    return myClassName;
  }

  public String[] getAttributes() {
    return myAttributes.keySet().toArray(new String[myAttributes.size()]);
  }

  public AntAttributeType getAttributeType(final String attr) {
    return myAttributes.get(attr);
  }

  public AntTaskDefinition[] getNestedElements() {
    AntTaskDefinition[] result = EMPTY_ARRAY;
    final int count = myNestedClassNames.size();
    if (count > 0) {
      result = new AntTaskDefinition[count];
      int index = 0;
      for (String className : myNestedClassNames) {
        result[index++] = myProject.getTaskDefinition(className);
      }
    }
    return result;
  }

  public AntTaskDefinition getTaskDefinition(final String className) {
    return myProject.getTaskDefinition(className);
  }

  public void registerNestedTask(final String taskClassName) {
    myNestedClassNames.add(taskClassName);
  }


  public AntTaskDefinition clone() {
    return new AntTaskDefinitionImpl(myProject, getClassName(), getNamespace(), getClassName(),
                                     new HashMap<String, AntAttributeType>(myAttributes), new HashSet<String>(myNestedClassNames));
  }
}
