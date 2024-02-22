// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.template;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TemplatesState {
  @SuppressWarnings("SpellCheckingInspection")
  @OptionTag("defaultTempalteName")
  String oldDefaultTemplateName;

  @Attribute
  public String defaultTemplateName = "";

  public final List<TemplateResource> templates = new ArrayList<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TemplatesState state = (TemplatesState)o;
    return Comparing.strEqual(defaultTemplateName, state.defaultTemplateName) &&
           Objects.equals(templates, state.templates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(StringUtil.nullize(defaultTemplateName), templates);
  }
}