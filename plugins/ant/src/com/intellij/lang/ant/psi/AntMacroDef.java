package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;

public interface AntMacroDef extends AntStructuredElement {
  AntTypeDefinition getMacroDefinition();
}
