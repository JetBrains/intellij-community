// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public abstract class NamedScriptableDefinition implements JDOMExternalizable {
  private static final @NonNls String NAME = "name";
  private static final @NonNls String SCRIPT = "script";
  private String name;
  private String scriptCodeConstraint;

  public NamedScriptableDefinition() {
    name = "";
    scriptCodeConstraint = "";
  }

  NamedScriptableDefinition(NamedScriptableDefinition definition) {
    name = definition.name;
    scriptCodeConstraint = definition.scriptCodeConstraint;
  }

  public abstract NamedScriptableDefinition copy();

  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  public @NotNull String getScriptCodeConstraint() {
    return scriptCodeConstraint;
  }

  public void setScriptCodeConstraint(@NotNull String scriptCodeConstraint) {
    this.scriptCodeConstraint = "\"\"".equals(scriptCodeConstraint) ? "" : scriptCodeConstraint;
  }

  @Override
  public void readExternal(Element element) {
    final Attribute attribute = element.getAttribute(NAME);
    name = attribute != null ? attribute.getValue() : "";

    final String script = element.getAttributeValue(SCRIPT);
    scriptCodeConstraint = script != null ? script : "";
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(NAME,name);
    if (!scriptCodeConstraint.isEmpty()) element.setAttribute(SCRIPT, scriptCodeConstraint);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NamedScriptableDefinition that)) return false;

    return name.equals(that.name) && scriptCodeConstraint.equals(that.scriptCodeConstraint);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + scriptCodeConstraint.hashCode();
  }
}
