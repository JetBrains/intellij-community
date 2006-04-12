package com.intellij.lang.ant.psi.introspection;

public interface AntTaskDefinition extends Cloneable {

  AntTaskDefinition[] EMPTY_ARRAY = new AntTaskDefinition[0];

  String getName();

  String getNamespace();

  String getClassName();

  String[] getAttributes();

  AntAttributeType getAttributeType(String attr);

  AntTaskDefinition[] getNestedElements();

  AntTaskDefinition getTaskDefinition(String className);

  void registerNestedTask(String taskClassName);

  AntTaskDefinition clone();
}
