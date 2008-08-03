package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;

public interface AntScriptDef extends AntTask {
  AntTypeDefinition getScriptDefinition();
}