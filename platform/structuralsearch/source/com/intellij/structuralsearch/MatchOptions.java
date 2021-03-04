// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.impl.matcher.compiler.StringToConstraintsTransformer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MatchOptions implements JDOMExternalizable {

  private final Map<String, MatchVariableConstraint> variableConstraints;
  private boolean looseMatching;
  private boolean recursiveSearch;
  private boolean caseSensitiveMatch;
  private String myUnknownFileType;
  private LanguageFileType myFileType;
  private Language myDialect;
  private SearchScope scope;
  private Scopes.Type scopeType;
  private String scopeDescriptor;
  private boolean searchInjectedCode;
  @NotNull
  private String pattern;

  private String myPatternContextId;

  @NonNls private static final String TEXT_ATTRIBUTE_NAME = "text";
  @NonNls private static final String LOOSE_MATCHING_ATTRIBUTE_NAME = "loose";
  @NonNls private static final String RECURSIVE_ATTRIBUTE_NAME = "recursive";
  @NonNls private static final String CASESENSITIVE_ATTRIBUTE_NAME = "caseInsensitive";
  @NonNls private static final String CONSTRAINT_TAG_NAME = "constraint";
  @NonNls private static final String FILE_TYPE_ATTR_NAME = "type";
  @NonNls private static final String DIALECT_ATTR_NAME = "dialect";
  @NonNls private static final String PATTERN_CONTEXT_ATTR_NAME = "pattern_context";
  @NonNls private static final String SCOPE_TYPE = "scope_type";
  @NonNls private static final String SCOPE_DESCRIPTOR = "scope_descriptor";
  @NonNls private static final String SEARCH_INJECTED_CODE = "search_injected";

  @NonNls public static final String INSTANCE_MODIFIER_NAME = "Instance";
  @NonNls public static final String MODIFIER_ANNOTATION_NAME = "Modifier";

  public MatchOptions() {
    variableConstraints = new LinkedHashMap<>();
    looseMatching = true;
    searchInjectedCode = true;
    pattern = "";
  }

  MatchOptions(MatchOptions options) {
    variableConstraints = new LinkedHashMap<>(options.variableConstraints.size());
    options.variableConstraints.forEach((key, value) -> variableConstraints.put(key, value.copy())); // deep copy
    looseMatching = options.looseMatching;
    recursiveSearch = options.recursiveSearch;
    caseSensitiveMatch = options.caseSensitiveMatch;
    myUnknownFileType = options.myUnknownFileType;
    myFileType = options.myFileType;
    myDialect = options.myDialect;
    scope = options.scope;
    scopeType = options.scopeType;
    scopeDescriptor = options.scopeDescriptor;
    searchInjectedCode = options.searchInjectedCode;
    pattern = options.pattern;
    myPatternContextId = options.myPatternContextId;
  }

  @NotNull
  public MatchOptions copy() {
    return new MatchOptions(this);
  }

  public void initScope(@NotNull Project project) {
    if (scope == null && scopeType != null && scopeDescriptor != null) {
      scope = Scopes.createScope(project, scopeDescriptor, scopeType);
    }
  }

  public void addVariableConstraint(@NotNull MatchVariableConstraint constraint) {
    variableConstraints.put(constraint.getName(), constraint);
  }

  public MatchVariableConstraint addNewVariableConstraint(@NotNull String name) {
    final MatchVariableConstraint constraint = new MatchVariableConstraint(name);
    variableConstraints.put(name, constraint);
    return constraint;
  }

  public Set<String> getUsedVariableNames() {
    final Set<String> set = TemplateImplUtil.parseVariableNames(pattern);
    set.add(Configuration.CONTEXT_VAR_NAME);
    return set;
  }

  public void removeUnusedVariables() {
    variableConstraints.keySet().removeIf(key -> !getUsedVariableNames().contains(key));
  }

  public MatchVariableConstraint getVariableConstraint(String name) {
    return variableConstraints.get(name);
  }

  public Set<String> getVariableConstraintNames() {
    return Collections.unmodifiableSet(variableConstraints.keySet());
  }

  public void setCaseSensitiveMatch(boolean caseSensitiveMatch) {
    this.caseSensitiveMatch = caseSensitiveMatch;
  }

  public boolean isCaseSensitiveMatch() {
    return caseSensitiveMatch;
  }

  public String toString() {
    return "match options:\n" +
           "pattern:\n" + pattern +
           "\nscope:\n" + ((scope != null) ? scope.toString() : "undefined scope") +
           "\nrecursive: " + recursiveSearch +
           "\ncase sensitive: " + caseSensitiveMatch +
           "\nloose: " + looseMatching;
  }

  public boolean isRecursiveSearch() {
    return recursiveSearch;
  }

  public void setRecursiveSearch(boolean recursiveSearch) {
    this.recursiveSearch = recursiveSearch;
  }

  public boolean isLooseMatching() {
    return looseMatching;
  }

  public void setLooseMatching(boolean looseMatching) {
    this.looseMatching = looseMatching;
  }

  public void setSearchPattern(@NotNull String text) {
    pattern = text;
  }

  public @NlsSafe @NotNull String getSearchPattern() {
    return pattern;
  }

  public void fillSearchCriteria(@NotNull String criteria) {
    if (!variableConstraints.isEmpty()) variableConstraints.clear();
    StringToConstraintsTransformer.transformCriteria(criteria, this);
  }

  @Nullable
  public SearchScope getScope() {
    return scope;
  }

  public void setScope(SearchScope scope) {
    scopeType = null;
    scopeDescriptor = null;
    this.scope = scope;
  }

  public boolean isSearchInjectedCode() {
    return searchInjectedCode;
  }

  public void setSearchInjectedCode(boolean injectedCode) {
    searchInjectedCode = injectedCode;
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(TEXT_ATTRIBUTE_NAME, pattern);
    if (!looseMatching) {
      element.setAttribute(LOOSE_MATCHING_ATTRIBUTE_NAME, "false");
    }
    element.setAttribute(RECURSIVE_ATTRIBUTE_NAME,String.valueOf(recursiveSearch));
    element.setAttribute(CASESENSITIVE_ATTRIBUTE_NAME,String.valueOf(caseSensitiveMatch));

    if (myFileType != null) {
      element.setAttribute(FILE_TYPE_ATTR_NAME, myFileType.getName());
    }
    else if (myUnknownFileType != null) {
      element.setAttribute(FILE_TYPE_ATTR_NAME, myUnknownFileType);
    }
    if (myDialect != null && (myFileType == null || myFileType.getLanguage() != myDialect)) {
      element.setAttribute(DIALECT_ATTR_NAME, myDialect.getID());
    }
    if (myPatternContextId != null) {
      element.setAttribute(PATTERN_CONTEXT_ATTR_NAME, myPatternContextId);
    }

    if (scope != null) {
      element.setAttribute(SCOPE_TYPE, Scopes.getType(scope).toString()).setAttribute(SCOPE_DESCRIPTOR, Scopes.getDescriptor(scope));
    }
    if (!searchInjectedCode) {
      element.setAttribute(SEARCH_INJECTED_CODE, "false");
    }

    final Set<String> constraintNames = getUsedVariableNames();
    for (final MatchVariableConstraint matchVariableConstraint : variableConstraints.values()) {
      if (!constraintNames.contains(matchVariableConstraint.getName())) {
        continue;
      }
      final Element infoElement = new Element(CONSTRAINT_TAG_NAME);
      element.addContent(infoElement);
      matchVariableConstraint.writeExternal(infoElement);
    }
  }

  @Override
  public void readExternal(Element element) {
    pattern = StringUtil.notNullize(element.getAttributeValue(TEXT_ATTRIBUTE_NAME));

    looseMatching = MatchVariableConstraint.getBooleanValue(element, LOOSE_MATCHING_ATTRIBUTE_NAME, true);

    recursiveSearch = MatchVariableConstraint.getBooleanValue(element, RECURSIVE_ATTRIBUTE_NAME, false);
    caseSensitiveMatch = MatchVariableConstraint.getBooleanValue(element, CASESENSITIVE_ATTRIBUTE_NAME, false);

    myUnknownFileType = element.getAttributeValue(FILE_TYPE_ATTR_NAME);
    myFileType = (myUnknownFileType == null) ? null : StructuralSearchUtil.getSuitableFileTypeByName(myUnknownFileType);
    if (myFileType != null) {
      myUnknownFileType = null;
    }
    myDialect = Language.findLanguageByID(element.getAttributeValue(DIALECT_ATTR_NAME));
    myPatternContextId = element.getAttributeValue(PATTERN_CONTEXT_ATTR_NAME);

    final String value = element.getAttributeValue(SCOPE_TYPE);
    scopeType = (value == null) ? null : Scopes.Type.valueOf(value);
    scopeDescriptor = element.getAttributeValue(SCOPE_DESCRIPTOR);
    searchInjectedCode = MatchVariableConstraint.getBooleanValue(element, SEARCH_INJECTED_CODE, true);

    for (final Element element1 : element.getChildren(CONSTRAINT_TAG_NAME)) {
      final MatchVariableConstraint constraint = new MatchVariableConstraint();
      constraint.readExternal(element1);
      addVariableConstraint(constraint);
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchOptions)) return false;

    final MatchOptions matchOptions = (MatchOptions)o;

    if (caseSensitiveMatch != matchOptions.caseSensitiveMatch) return false;
    if (looseMatching != matchOptions.looseMatching) return false;
    if (recursiveSearch != matchOptions.recursiveSearch) return false;
    if (!Objects.equals(scope, matchOptions.scope)) return false;
    if (searchInjectedCode != matchOptions.searchInjectedCode) return false;
    if (!pattern.equals(matchOptions.pattern)) return false;
    if (!variableConstraints.equals(matchOptions.variableConstraints)) return false;
    if (myUnknownFileType != matchOptions.myUnknownFileType) return false;
    if (myFileType != matchOptions.myFileType) return false;
    if (!Objects.equals(getDialect(), matchOptions.getDialect())) return false;
    if (!Objects.equals(myPatternContextId, matchOptions.myPatternContextId)) return false;

    return true;
  }

  public int hashCode() {
    int result = looseMatching ? 1 : 0;
    result = 29 * result + (recursiveSearch ? 1 : 0);
    result = 29 * result + (caseSensitiveMatch ? 1 : 0);
    result = 29 * result + pattern.hashCode();
    result = 29 * result + variableConstraints.hashCode();
    if (scope != null) result = 29 * result + scope.hashCode();
    result = 29 * result + (searchInjectedCode ? 1 : 0);
    if (myUnknownFileType != null) result = 29 * result + myUnknownFileType.hashCode();
    if (myFileType != null) result = 29 * result + myFileType.hashCode();
    if (myDialect != null) result = 29 * result + myDialect.hashCode();
    if (myPatternContextId != null) result = 29 * result + myPatternContextId.hashCode();
    return result;
  }

  public void setFileType(@NotNull LanguageFileType fileType) {
    myFileType = fileType;
  }

  @Nullable
  public LanguageFileType getFileType() {
    return myFileType;
  }

  @Nullable
  public Language getDialect() {
    if (myDialect == null) {
      final LanguageFileType fileType = getFileType();
      return (fileType == null) ? null : fileType.getLanguage();
    }
    return myDialect;
  }

  public void setDialect(Language dialect) {
    myDialect = dialect;
  }

  public PatternContext getPatternContext() {
    final Language dialect = getDialect();
    return (dialect == null) ? null : StructuralSearchUtil.findPatternContextByID(myPatternContextId, dialect);
  }

  public void setPatternContext(PatternContext patternContext) {
    myPatternContextId = (patternContext == null) ? null : patternContext.getId();
  }
}
