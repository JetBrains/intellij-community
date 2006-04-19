package com.intellij.lang.ant.psi;

public interface AntTaskDef extends AntTask {

  String getDefinedName();

  String getClassName();

  String getClassPath();
}
