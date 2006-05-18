package com.intellij.lang.ant.psi.introspection.impl;

import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AntTypeDefinitionImpl implements AntTypeDefinition {

  private AntTypeId myTypeId;
  private String myClassName;
  private boolean myIsTask;
  /**
   * Attribute names to their types.
   */
  private final Map<String, AntAttributeType> myAttributes;
  /**
   * Task ids to their class names.
   */
  private final Map<AntTypeId, String> myNestedClassNames;
  private final PsiElement myDefiningElement;

  public AntTypeDefinitionImpl(final AntTypeDefinitionImpl base) {
    this(base.getTypeId(), base.getClassName(), base.isTask(),
         new HashMap<String, AntAttributeType>(base.myAttributes),
         new HashMap<AntTypeId, String>(base.myNestedClassNames));
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask,
                               @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements) {
    this(id, className, isTask, attributes, nestedElements, null);
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask,
                               @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements,
                               final PsiElement definingElement) {
    myTypeId = id;
    myClassName = className;
    myIsTask = isTask;
    attributes.put("id", AntAttributeType.STRING);
    myAttributes = attributes;
    myNestedClassNames = nestedElements;
    myDefiningElement = definingElement;
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

  public PsiElement getDefiningElement() {
    return myDefiningElement;
  }

  public void setIsTask(final boolean isTask) {
    myIsTask = isTask;
  }

  public void setClassName(final String className) {
    myClassName = className;
  }
}
