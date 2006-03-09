package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;

import java.util.Properties;

public interface AntPropertySet extends AntElement {

  @NotNull
  Properties getProperties();
}
