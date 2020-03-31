// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.generate.template;

import com.intellij.util.xmlb.annotations.OptionTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TemplatesState {
  @OptionTag("defaultTempalteName")
  public String defaultTemplateName = "";
  public final List<TemplateResource> templates = new ArrayList<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TemplatesState state = (TemplatesState)o;
    return Objects.equals(defaultTemplateName, state.defaultTemplateName) &&
           Objects.equals(templates, state.templates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultTemplateName, templates);
  }
}