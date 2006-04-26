package com.intellij.lang.ant.psi.introspection.impl;

import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AntTypeDefinitionImpl implements AntTypeDefinition {

  private AntTypeId myTypeId;
  private final String myClassName;
  private final boolean myIsTask;
  /**
   * Attribute names to their types.
   */
  private final Map<String, AntAttributeType> myAttributes;
  /**
   * Task ids to their class names.
   */
  private final Map<AntTypeId, String> myNestedClassNames;

  public AntTypeDefinitionImpl(final AntTypeDefinitionImpl base) {
    myTypeId = base.getTypeId();
    myClassName = base.getClassName();
    myIsTask = base.isTask();
    myAttributes = new HashMap<String, AntAttributeType>(base.myAttributes);
    myNestedClassNames = new HashMap<AntTypeId, String>(base.myNestedClassNames);
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask,
                               @NonNls final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements) {
    myTypeId = id;
    myClassName = className;
    myIsTask = isTask;
    attributes.put("id", AntAttributeType.STRING);
    myAttributes = attributes;
    myNestedClassNames = nestedElements;
  }

  public AntTypeId getTypeId() {
    return myTypeId;
  }

  public void setTypeId(final AntTypeId id) {
    myTypeId = id;
  }

  public String getClassName() {
    return myClassName;
  }

  public boolean isTask() {
    return myIsTask;
  }

  public String[] getAttributes() {
    return myAttributes.keySet().toArray(new String[myAttributes.size()]);
  }

  public AntAttributeType getAttributeType(final String attr) {
    return myAttributes.get(attr);
  }

  @SuppressWarnings({"unchecked"})
  public AntTypeId[] getNestedElements() {
    return myNestedClassNames.keySet().toArray(new AntTypeId[myNestedClassNames.size()]);
  }

  @Nullable
  public String getNestedClassName(final AntTypeId id) {
    return myNestedClassNames.get(id);
  }

  public void registerNestedType(final AntTypeId id, String taskClassName) {
    myNestedClassNames.put(id, taskClassName);
  }
}
