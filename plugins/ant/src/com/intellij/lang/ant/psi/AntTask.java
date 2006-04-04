package com.intellij.lang.ant.psi;

public interface AntTask extends AntElement {

  String[] getAttributeNames();

  Class getAttributeType(final String attributeName);

  String[] getNestedElements();
}
