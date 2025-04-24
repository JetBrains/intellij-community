// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui.classFilter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;

@Tag("class-filter")
public class ClassFilter implements JDOMExternalizable, Cloneable{
  private static final Logger LOG = Logger.getInstance(ClassFilter.class);
  public static final ClassFilter[] EMPTY_ARRAY = new ClassFilter[0];

  @Attribute("pattern")
  public String PATTERN = "";
  @Attribute("enabled")
  public boolean ENABLED = true;

  @Attribute("include")
  public boolean INCLUDE = true;

  private Matcher myMatcher;  // to speed up matching

  public ClassFilter() {
  }

  public ClassFilter(String pattern) {
    PATTERN = pattern;
  }

  @Transient
  public String getPattern() {
    return PATTERN;
  }

  @Transient
  public boolean isEnabled() {
    return ENABLED;
  }

  @Transient
  public boolean isInclude() {
    return INCLUDE;
  }

  public void setPattern(String pattern) {
    if (pattern != null && !pattern.equals(PATTERN)) {
      PATTERN = pattern;
      getMatcher(""); // compile the pattern in advance to have it ready before doing deepCopy
    }
  }
  public void setEnabled(boolean value) {
    ENABLED = value;
  }

  public void setInclude(boolean value) {
    INCLUDE = value;
  }

  @Override
  public String toString() {
    return getPattern();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.addContent(new Element("option").setAttribute("name", "PATTERN").setAttribute("value", PATTERN));
    element.addContent(new Element("option").setAttribute("name", "ENABLED").setAttribute("value", String.valueOf(ENABLED)));
    if (!INCLUDE) {
      element.addContent(new Element("option").setAttribute("name", "INCLUDE").setAttribute("value", "false"));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassFilter)) return false;
    ClassFilter classFilter = (ClassFilter)o;
    return isEnabled() == classFilter.isEnabled() && getPattern().equals(classFilter.getPattern());
  }

  @Override
  public int hashCode() {
    int result;
    result = PATTERN.hashCode();
    result = 29 * result + (ENABLED ? 1 : 0);
    return result;
  }

  @Override
  public ClassFilter clone() {
    try {
      return (ClassFilter) super.clone();
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean matches(String name) {
    return getMatcher(name).matches();
  }

  private Matcher getMatcher(final String name) {
    if (myMatcher == null) {
      // need to quote dots, dollars, etc.
      myMatcher = PatternUtil.fromMask(getPattern()).matcher(name);
    }
    else {
      myMatcher.reset(name);
    }
    return myMatcher;
  }

  public static ClassFilter @NotNull [] deepCopyOf(ClassFilter @NotNull [] original) {
    return ContainerUtil.map(original, ClassFilter::clone, EMPTY_ARRAY);
  }

}