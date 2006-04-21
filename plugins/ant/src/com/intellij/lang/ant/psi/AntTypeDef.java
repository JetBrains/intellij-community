package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;

public interface AntTypeDef extends AntTask {

  String getDefinedName();

  String getClassName();

  String getClassPath();

  String getClassPathRef();

  String getLoaderRef();

  String getFormat();

  String getUri();

  AntTypeDefinition getDefinition();
}
