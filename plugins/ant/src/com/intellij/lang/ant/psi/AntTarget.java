package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntStructuredElement {
  enum ConditionalAttribute {
    IF("if"), UNLESS("unless");

    private final String xmlName;

    ConditionalAttribute(@NonNls final String xmlName) {
      this.xmlName = xmlName;
    }

    public String getXmlName() {
      return xmlName;
    }
  }
  
  AntTarget[] EMPTY_ARRAY = new AntTarget[0];

  /**
   * @return If project is named, target name prefixed with project name and the dot,
   * otherwise target name equal to that returned by {@link #getName()}.
   */
  @NotNull
  String getQualifiedName();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  @Nullable
  String getConditionalPropertyName(ConditionalAttribute attrib);
  
  void setDependsTargets(@NotNull AntTarget[] targets);

}
