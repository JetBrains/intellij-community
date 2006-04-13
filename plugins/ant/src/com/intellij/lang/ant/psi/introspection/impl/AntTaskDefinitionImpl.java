package com.intellij.lang.ant.psi.introspection.impl;

import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AntTaskDefinitionImpl implements AntTaskDefinition {

  private Pair<String, String> myTaskId;
  private final String myClassName;
  /**
   * Attribute names to their types.
   */
  private final Map<String, AntAttributeType> myAttributes;
  /**
   * Task ids to their class names.
   */
  private final Map<Pair<String, String>, String> myNestedClassNames;

  public AntTaskDefinitionImpl(final AntTaskDefinitionImpl base) {
    myTaskId = base.getTaskId();
    myClassName = base.getClassName();
    myAttributes = new HashMap<String, AntAttributeType>(base.myAttributes);
    myNestedClassNames = new HashMap<Pair<String, String>, String>(base.myNestedClassNames);
  }

  public AntTaskDefinitionImpl(final Pair<String, String> taskId,
                               final String className,
                               final Map<String, AntAttributeType> attributes,
                               final Map<Pair<String, String>, String> nestedElements) {
    myTaskId = taskId;
    myClassName = className;
    myAttributes = attributes;
    myNestedClassNames = nestedElements;
  }

  public Pair<String, String> getTaskId() {
    return myTaskId;
  }

  public void setTaskId(final Pair<String, String> taskId) {
    myTaskId = taskId;
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

  @SuppressWarnings({"unchecked"})
  public Pair<String, String>[] getNestedElements() {
    return myNestedClassNames.keySet().toArray(new Pair[myNestedClassNames.size()]);
  }

  @Nullable
  public String getNestedClassName(Pair<String, String> taskId) {
    return myNestedClassNames.get(taskId);
  }

  public void registerNestedTask(Pair<String, String> taskId, String taskClassName) {
    myNestedClassNames.put(taskId, taskClassName);
  }
}
