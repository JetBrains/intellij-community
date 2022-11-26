// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.replace;

import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.ReplacementVariableDefinition;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class ReplaceOptions implements JDOMExternalizable {
  private final Map<String, ReplacementVariableDefinition> variableDefs;
  private String replacement;
  private boolean toShortenFQN;
  private boolean myToReformatAccordingToStyle;
  private boolean myToUseStaticImport;
  @NotNull
  private final MatchOptions matchOptions;

  @NonNls private static final String REFORMAT_ATTR_NAME = "reformatAccordingToStyle";
  @NonNls private static final String REPLACEMENT_ATTR_NAME = "replacement";
  @NonNls private static final String SHORTEN_FQN_ATTR_NAME = "shortenFQN";
  @NonNls private static final String USE_STATIC_IMPORT_ATTR_NAME = "useStaticImport";

  @NonNls private static final String VARIABLE_DEFINITION_TAG_NAME = "variableDefinition";

  public ReplaceOptions() {
    this(new MatchOptions());
  }

  public ReplaceOptions(@NotNull MatchOptions matchOptions) {
    variableDefs = new LinkedHashMap<>();
    this.matchOptions = matchOptions;
    replacement = matchOptions.getSearchPattern();
    myToUseStaticImport = false;
  }

  private ReplaceOptions(@NotNull ReplaceOptions options) {
    variableDefs = new LinkedHashMap<>(options.variableDefs.size());
    options.variableDefs.forEach((key, value) -> variableDefs.put(key, value.copy())); // deep copy
    replacement = options.replacement;
    toShortenFQN = options.toShortenFQN;
    myToReformatAccordingToStyle = options.myToReformatAccordingToStyle;
    myToUseStaticImport = options.myToUseStaticImport;
    matchOptions = options.matchOptions.copy(); // deep copy
  }

  @NotNull
  public ReplaceOptions copy() {
    return new ReplaceOptions(this);
  }

  @NotNull
  public @NlsSafe String getReplacement() {
    return replacement;
  }

  public void setReplacement(@NotNull String replacement) {
    this.replacement = replacement;
  }

  public boolean isToShortenFQN() {
    return toShortenFQN;
  }

  public void setToShortenFQN(boolean shortedFQN) {
    this.toShortenFQN = shortedFQN;
  }

  public boolean isToReformatAccordingToStyle() {
    return myToReformatAccordingToStyle;
  }

  public @NotNull MatchOptions getMatchOptions() {
    return matchOptions;
  }

  public void setToReformatAccordingToStyle(boolean reformatAccordingToStyle) {
    myToReformatAccordingToStyle = reformatAccordingToStyle;
  }

  public boolean isToUseStaticImport() {
    return myToUseStaticImport;
  }

  public void setToUseStaticImport(boolean useStaticImport) {
    myToUseStaticImport = useStaticImport;
  }

  @NotNull
  private Set<String> getUsedVariableNames() {
    return TemplateImplUtil.parseVariableNames(replacement);
  }

  public void removeUnusedVariables() {
    variableDefs.keySet().removeIf(key -> !getUsedVariableNames().contains(key));
  }

  @Override
  public void readExternal(Element element) {
    matchOptions.readExternal(element);

    Attribute attribute = element.getAttribute(REFORMAT_ATTR_NAME);
    try {
      myToReformatAccordingToStyle = attribute == null || attribute.getBooleanValue();
    } catch(DataConversionException ignored) {}

    attribute = element.getAttribute(SHORTEN_FQN_ATTR_NAME);
    try {
      toShortenFQN = attribute == null || attribute.getBooleanValue();
    } catch(DataConversionException ignored) {}

    attribute = element.getAttribute(USE_STATIC_IMPORT_ATTR_NAME);
    if (attribute != null) { // old saved configurations without this attribute present
      try {
        myToUseStaticImport = attribute.getBooleanValue();
      }
      catch (DataConversionException ignore) {}
    }

    replacement = element.getAttributeValue(REPLACEMENT_ATTR_NAME);

    for (final Element child : element.getChildren(VARIABLE_DEFINITION_TAG_NAME)) {
      final ReplacementVariableDefinition variableDefinition = new ReplacementVariableDefinition();
      variableDefinition.readExternal(child);
      addVariableDefinition(variableDefinition);
    }
  }

  @Override
  public void writeExternal(Element element) {
    matchOptions.writeExternal(element);

    element.setAttribute(REFORMAT_ATTR_NAME,String.valueOf(myToReformatAccordingToStyle));
    element.setAttribute(SHORTEN_FQN_ATTR_NAME,String.valueOf(toShortenFQN));
    if (myToUseStaticImport) {
      element.setAttribute(USE_STATIC_IMPORT_ATTR_NAME, String.valueOf(true));
    }
    element.setAttribute(REPLACEMENT_ATTR_NAME,replacement);

    final Set<String> nameSet = getUsedVariableNames();
    for (final ReplacementVariableDefinition variableDefinition : variableDefs.values()) {
      if (!nameSet.contains(variableDefinition.getName())) {
        continue;
      }
      final Element infoElement = new Element(VARIABLE_DEFINITION_TAG_NAME);
      element.addContent(infoElement);
      variableDefinition.writeExternal(infoElement);
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReplaceOptions)) return false;

    final ReplaceOptions replaceOptions = (ReplaceOptions)o;

    if (myToReformatAccordingToStyle != replaceOptions.myToReformatAccordingToStyle) return false;
    if (toShortenFQN != replaceOptions.toShortenFQN) return false;
    if (myToUseStaticImport != replaceOptions.myToUseStaticImport) return false;
    if (!matchOptions.equals(replaceOptions.matchOptions)) return false;
    if (!Objects.equals(replacement, replaceOptions.replacement)) return false;
    if (!variableDefs.equals(replaceOptions.variableDefs)) return false;

    return true;
  }

  public int hashCode() {
    int result = replacement.hashCode();
    result = 29 * result + (toShortenFQN ? 1 : 0);
    result = 29 * result + (myToReformatAccordingToStyle ? 1 : 0);
    result = 29 * result + (myToUseStaticImport ? 1 : 0);
    result = 29 * result + matchOptions.hashCode();
    result = 29 * result + variableDefs.hashCode();
    return result;
  }

  public ReplacementVariableDefinition getVariableDefinition(@NotNull String name) {
    return variableDefs != null ? variableDefs.get(name): null;
  }

  public void addVariableDefinition(@NotNull ReplacementVariableDefinition definition) {
    variableDefs.put(definition.getName(), definition);
  }

  @NotNull
  public ReplacementVariableDefinition addNewVariableDefinition(@NotNull String name) {
    final ReplacementVariableDefinition definition = new ReplacementVariableDefinition(name);
    variableDefs.put(name, definition);
    return definition;
  }

  @NotNull
  public Collection<ReplacementVariableDefinition> getVariableDefinitions() {
    return variableDefs.values();
  }

  public void clearVariableDefinitions() {
    variableDefs.clear();
  }
}
