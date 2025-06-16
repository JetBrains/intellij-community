// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Maxim.Mossienko
 */
public final class MatchVariableConstraint extends NamedScriptableDefinition {
  private static final Pattern VALID_CONSTRAINT_NAME = Pattern.compile("[a-z][A-Za-z\\d]*");

  private Map<String, String> additionalConstraints;

  private @NotNull String regExp = "";
  private boolean invertRegExp;
  private boolean withinHierarchy;
  private boolean strictlyWithinHierarchy;
  private boolean wholeWordsOnly;
  private int minCount = 1;
  private int maxCount = 1;
  private boolean greedy = true;
  private boolean invertReference;
  private @NotNull String referenceConstraint = "";
  private @NotNull String referenceConstraintName = "";
  private boolean partOfSearchResults;
  private @NotNull String nameOfExprType = "";
  private @NotNull String expressionTypes = "";
  private boolean invertExprType;
  private boolean exprTypeWithinHierarchy;

  private @NotNull String nameOfFormalArgType = "";
  private @NotNull String expectedTypes = "";
  private boolean invertFormalType;
  private boolean formalArgTypeWithinHierarchy;

  private @NotNull String withinConstraint = "";
  private @NotNull String containsConstraint = "";
  private boolean invertContainsConstraint;
  private boolean invertWithinConstraint;

  private @NotNull String contextConstraint = "";

  private static final @NonNls String REFERENCE_CONDITION = "reference";
  private static final @NonNls String NAME_OF_EXPRTYPE = "nameOfExprType";
  private static final @NonNls String NAME_OF_FORMALTYPE = "nameOfFormalType";
  private static final @NonNls String REGEXP = "regexp";
  private static final @NonNls String EXPRTYPE_WITHIN_HIERARCHY = "exprTypeWithinHierarchy";
  private static final @NonNls String FORMALTYPE_WITHIN_HIERARCHY = "formalTypeWithinHierarchy";

  private static final @NonNls String WITHIN_HIERARCHY = "withinHierarchy";
  private static final @NonNls String MAX_OCCURS = "maxCount";
  private static final @NonNls String MIN_OCCURS = "minCount";

  private static final @NonNls String NEGATE_NAME_CONDITION = "negateName";
  private static final @NonNls String NEGATE_EXPRTYPE_CONDITION = "negateExprType";
  private static final @NonNls String NEGATE_FORMALTYPE_CONDITION = "negateFormalType";
  private static final @NonNls String NEGATE_CONTAINS_CONDITION = "negateContains";
  private static final @NonNls String NEGATE_WITHIN_CONDITION = "negateWithin";
  private static final @NonNls String NEGATE_REFERENCE_CONDITION = "negateReference";
  private static final @NonNls String WITHIN_CONDITION = "within";
  private static final @NonNls String CONTAINS_CONDITION = "contains";
  private static final @NonNls String TARGET = "target";
  private static final @NonNls String CONTEXT = "context";

  private static final @NonNls String WHOLE_WORDS_ONLY = "wholeWordsOnly";
  private static final @NonNls String TRUE = Boolean.TRUE.toString();

  public MatchVariableConstraint() {}

  public MatchVariableConstraint(@NotNull String name) {
    setName(name);
  }

  private MatchVariableConstraint(@NotNull MatchVariableConstraint constraint) {
    super(constraint);
    regExp = constraint.regExp;
    invertRegExp = constraint.invertRegExp;
    withinHierarchy = constraint.withinHierarchy;
    strictlyWithinHierarchy = constraint.strictlyWithinHierarchy;
    wholeWordsOnly = constraint.wholeWordsOnly;
    minCount = constraint.minCount;
    maxCount = constraint.maxCount;
    greedy = constraint.greedy;
    invertReference = constraint.invertReference;
    referenceConstraint = constraint.referenceConstraint;
    referenceConstraintName = constraint.referenceConstraintName;
    partOfSearchResults = constraint.partOfSearchResults;
    nameOfExprType = constraint.nameOfExprType;
    expressionTypes = constraint.expressionTypes;
    invertExprType = constraint.invertExprType;
    exprTypeWithinHierarchy = constraint.exprTypeWithinHierarchy;
    nameOfFormalArgType = constraint.nameOfFormalArgType;
    expectedTypes = constraint.expectedTypes;
    invertFormalType = constraint.invertFormalType;
    formalArgTypeWithinHierarchy = constraint.formalArgTypeWithinHierarchy;
    withinConstraint = constraint.withinConstraint;
    containsConstraint = constraint.containsConstraint;
    invertContainsConstraint = constraint.invertContainsConstraint;
    invertWithinConstraint = constraint.invertWithinConstraint;
    contextConstraint = constraint.contextConstraint;
    final Map<String, String> additionalConstraints = constraint.additionalConstraints;
    if (additionalConstraints != null) {
      for (Map.Entry<String, String> entry : additionalConstraints.entrySet()) {
        putAdditionalConstraint(entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public MatchVariableConstraint copy() {
    return new MatchVariableConstraint(this);
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull String convertRegExpTypeToTypeString(@NotNull String regexp) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0, length = regexp.length(); i < length; i++) {
      final int c = regexp.codePointAt(i);
      if (c == '.') {
        if (i == length - 1 || !MatchUtil.isRegExpMetaChar(regexp.codePointAt(i + 1))) {
          result.append('.'); // consider dot not followed by other meta char a mistake
        }
        else {
          return ""; // can't convert
        }
      }
      else if (c == '|') {
        result.append('|');
      }
      else if (c == '\\') {
        if (i + 1 < length) {
          result.appendCodePoint(regexp.codePointAt(i + 1));
          i++;
        }
        else {
          result.append('\\');
        }
      }
      else if (c == ']') {
        result.append(']');
      }
      else if (c == '(' || c == ')') {
        // do nothing
      }
      else if (MatchUtil.isRegExpMetaChar(c)) {
        return ""; // can't convert
      }
      else {
        result.appendCodePoint(c);
      }
    }
    return result.toString();
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull String convertTypeStringToRegExp(@NotNull String typeString) {
    final StringBuilder result = new StringBuilder();
    for (String type : StringUtil.split(typeString, "|")) {
      if (!result.isEmpty()) {
        result.append('|');
      }
      MatchUtil.shieldRegExpMetaChars(type.trim(), result);
    }
    return result.toString();
  }

  public boolean isGreedy() {
    return greedy;
  }

  public void setGreedy(boolean greedy) {
    this.greedy = greedy;
  }

  public @NotNull String getRegExp() {
    return regExp;
  }

  public void setRegExp(@NotNull String regExp) {
    this.regExp = regExp;
  }

  public boolean isInvertRegExp() {
    return invertRegExp;
  }

  public void setInvertRegExp(boolean invertRegExp) {
    this.invertRegExp = invertRegExp;
  }

  public boolean isWithinHierarchy() {
    return withinHierarchy;
  }

  public void setWithinHierarchy(boolean withinHierarchy) {
    this.withinHierarchy = withinHierarchy;
  }

  public int getMinCount() {
    return minCount;
  }

  public void setMinCount(int minCount) {
    this.minCount = minCount;
  }

  public int getMaxCount() {
    return maxCount;
  }

  public void setMaxCount(int maxCount) {
    this.maxCount = maxCount;
  }

  public boolean isPartOfSearchResults() {
    return partOfSearchResults;
  }

  public void setPartOfSearchResults(boolean partOfSearchResults) {
    this.partOfSearchResults = partOfSearchResults;
  }

  public boolean isInvertReference() {
    return invertReference;
  }

  public void setInvertReference(boolean invertReference) {
    this.invertReference = invertReference;
  }

  public @NotNull String getReferenceConstraint() {
    return referenceConstraint;
  }

  public void setReferenceConstraint(@NotNull String nameOfReferenceVar) {
    this.referenceConstraint = nameOfReferenceVar;
  }

  public @NotNull String getReferenceConstraintName() {
    return referenceConstraintName;
  }

  public void setReferenceConstraintName(@NotNull String referenceConstraintName) {
    this.referenceConstraintName = referenceConstraintName;
  }

  public boolean isStrictlyWithinHierarchy() {
    return strictlyWithinHierarchy;
  }

  public void setStrictlyWithinHierarchy(boolean strictlyWithinHierarchy) {
    this.strictlyWithinHierarchy = strictlyWithinHierarchy;
  }

  public @NotNull String getNameOfExprType() {
    return nameOfExprType;
  }

  public @NotNull String getExpressionTypes() {
    return expressionTypes;
  }

  public void setNameOfExprType(@NotNull String nameOfExprType) {
    this.nameOfExprType = nameOfExprType;
    this.expressionTypes = convertRegExpTypeToTypeString(nameOfExprType);
  }

  public void setExpressionTypes(@NotNull String expressionTypes) {
    this.expressionTypes = expressionTypes;
    this.nameOfExprType = convertTypeStringToRegExp(expressionTypes);
  }

  public boolean isRegexExprType() {
    return StringUtil.isEmpty(expressionTypes) && !StringUtil.isEmpty(nameOfExprType);
  }

  public boolean isInvertExprType() {
    return invertExprType;
  }

  public void setInvertExprType(boolean invertExprType) {
    this.invertExprType = invertExprType;
  }

  public boolean isExprTypeWithinHierarchy() {
    return exprTypeWithinHierarchy;
  }

  public void setExprTypeWithinHierarchy(boolean exprTypeWithinHierarchy) {
    this.exprTypeWithinHierarchy = exprTypeWithinHierarchy;
  }

  public boolean isWholeWordsOnly() {
    return wholeWordsOnly;
  }

  public void setWholeWordsOnly(boolean wholeWordsOnly) {
    this.wholeWordsOnly = wholeWordsOnly;
  }

  public @NotNull String getNameOfFormalArgType() {
    return nameOfFormalArgType;
  }

  public @NotNull String getExpectedTypes() {
    return expectedTypes;
  }

  public void setNameOfFormalArgType(@NotNull String nameOfFormalArgType) {
    this.nameOfFormalArgType = nameOfFormalArgType;
    this.expectedTypes = convertRegExpTypeToTypeString(nameOfFormalArgType);
  }

  public void setExpectedTypes(@NotNull String expectedTypes) {
    this.expectedTypes = expectedTypes;
    this.nameOfFormalArgType = convertTypeStringToRegExp(expectedTypes);
  }

  public boolean isRegexFormalType() {
    return StringUtil.isEmpty(expectedTypes) && !StringUtil.isEmpty(nameOfFormalArgType);
  }

  public boolean isInvertFormalType() {
    return invertFormalType;
  }

  public void setInvertFormalType(boolean invertFormalType) {
    this.invertFormalType = invertFormalType;
  }

  public boolean isFormalArgTypeWithinHierarchy() {
    return formalArgTypeWithinHierarchy;
  }

  public void setFormalArgTypeWithinHierarchy(boolean formalArgTypeWithinHierarchy) {
    this.formalArgTypeWithinHierarchy = formalArgTypeWithinHierarchy;
  }

  public @NotNull String getContextConstraint() {
    return contextConstraint;
  }

  public void setContextConstraint(@NotNull String contextConstraint) {
    this.contextConstraint = contextConstraint;
  }

  private static boolean isValidConstraintName(String name) {
    return VALID_CONSTRAINT_NAME.matcher(name).matches();
  }

  public void putAdditionalConstraint(String name, String value) {
    if (!isValidConstraintName(name)) {
      throw new IllegalArgumentException("Invalid constraint name");
    }
    if (additionalConstraints == null) {
      if (value == null) {
        return;
      }
      additionalConstraints = new HashMap<>();
    }
    if (value == null) {
      additionalConstraints.remove(name);
      if (additionalConstraints.isEmpty()) {
        additionalConstraints = null;
      }
    }
    else {
      additionalConstraints.put(name, value);
    }
  }

  public String getAdditionalConstraint(String name) {
    return (additionalConstraints == null) ? null : additionalConstraints.get(name);
  }

  /**
   * @return an unmodifiable map of all additional constraints.
   */
  public Map<String, String> getAllAdditionalConstraints() {
    return (additionalConstraints == null) ? Collections.emptyMap() : Collections.unmodifiableMap(additionalConstraints);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchVariableConstraint other)) return false;
    if (!super.equals(o)) return false;

    if (exprTypeWithinHierarchy != other.exprTypeWithinHierarchy) return false;
    if (formalArgTypeWithinHierarchy != other.formalArgTypeWithinHierarchy) return false;
    if (greedy != other.greedy) return false;
    if (invertExprType != other.invertExprType) return false;
    if (invertFormalType != other.invertFormalType) return false;
    if (invertReference != other.invertReference) return false;
    if (invertRegExp != other.invertRegExp) return false;
    if (maxCount != other.maxCount) return false;
    if (minCount != other.minCount) return false;
    if (partOfSearchResults != other.partOfSearchResults) return false;
    if (strictlyWithinHierarchy != other.strictlyWithinHierarchy) return false;
    if (wholeWordsOnly != other.wholeWordsOnly) return false;
    if (withinHierarchy != other.withinHierarchy) return false;
    if (!nameOfExprType.equals(other.nameOfExprType)) return false;
    if (!expressionTypes.equals(other.expressionTypes)) return false;
    if (!nameOfFormalArgType.equals(other.nameOfFormalArgType)) return false;
    if (!expectedTypes.equals(other.expectedTypes)) return false;
    if (!referenceConstraint.equals(other.referenceConstraint)) return false;
    if (!regExp.equals(other.regExp)) return false;
    if (!withinConstraint.equals(other.withinConstraint)) return false;
    if (!containsConstraint.equals(other.containsConstraint)) return false;
    if (invertWithinConstraint != other.invertWithinConstraint) return false;
    if (invertContainsConstraint != other.invertContainsConstraint) return false;
    if (!contextConstraint.equals(other.contextConstraint)) return false;
    if (additionalConstraints != null) {
       if (!additionalConstraints.equals(other.additionalConstraints)) return false;
    }
    else {
      return other.additionalConstraints == null;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + regExp.hashCode();
    result = 29 * result + (invertRegExp ? 1 : 0);
    result = 29 * result + (withinHierarchy ? 1 : 0);
    result = 29 * result + (strictlyWithinHierarchy ? 1 : 0);
    result = 29 * result + (wholeWordsOnly ? 1 : 0);
    result = 29 * result + minCount;
    result = 29 * result + maxCount;
    result = 29 * result + (greedy ? 1 : 0);
    result = 29 * result + (invertReference ? 1 : 0);
    result = 29 * result + referenceConstraint.hashCode();
    result = 29 * result + (partOfSearchResults ? 1 : 0);
    result = 29 * result + nameOfExprType.hashCode();
    result = 29 * result + expressionTypes.hashCode();
    result = 29 * result + (invertExprType ? 1 : 0);
    result = 29 * result + (exprTypeWithinHierarchy ? 1 : 0);
    result = 29 * result + nameOfFormalArgType.hashCode();
    result = 29 * result + expectedTypes.hashCode();
    result = 29 * result + (invertFormalType ? 1 : 0);
    result = 29 * result + (formalArgTypeWithinHierarchy ? 1 : 0);
    result = 29 * result + withinConstraint.hashCode();
    result = 29 * result + containsConstraint.hashCode();
    result = 29 * result + contextConstraint.hashCode();

    if (invertContainsConstraint) result = 29 * result + 1;
    if (invertWithinConstraint) result = 29 * result + 1;
    if (additionalConstraints != null) result = 29 * result + additionalConstraints.hashCode();
    return result;
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);

    regExp = StringUtil.notNullize(element.getAttributeValue(REGEXP));
    withinHierarchy = getBooleanValue(element, WITHIN_HIERARCHY, false);
    invertRegExp = getBooleanValue(element, NEGATE_NAME_CONDITION, false);
    wholeWordsOnly = getBooleanValue(element, WHOLE_WORDS_ONLY, false);

    nameOfExprType = StringUtil.notNullize(element.getAttributeValue(NAME_OF_EXPRTYPE));
    expressionTypes = convertRegExpTypeToTypeString(nameOfExprType);

    exprTypeWithinHierarchy = getBooleanValue(element, EXPRTYPE_WITHIN_HIERARCHY, false);
    invertExprType = getBooleanValue(element, NEGATE_EXPRTYPE_CONDITION, false);

    nameOfFormalArgType = StringUtil.notNullize(element.getAttributeValue(NAME_OF_FORMALTYPE));
    expectedTypes = convertRegExpTypeToTypeString(nameOfFormalArgType);

    formalArgTypeWithinHierarchy = getBooleanValue(element, FORMALTYPE_WITHIN_HIERARCHY, false);
    invertFormalType = getBooleanValue(element, NEGATE_FORMALTYPE_CONDITION, false);

    minCount = getIntValue(element, MIN_OCCURS, 1);
    maxCount = getIntValue(element, MAX_OCCURS, 1);

    referenceConstraint = StringUtil.notNullize(element.getAttributeValue(REFERENCE_CONDITION));
    invertReference = getBooleanValue(element, NEGATE_REFERENCE_CONDITION, false);

    containsConstraint = StringUtil.notNullize(element.getAttributeValue(CONTAINS_CONDITION));
    invertContainsConstraint = getBooleanValue(element, NEGATE_CONTAINS_CONDITION, false);

    withinConstraint = StringUtil.notNullize(element.getAttributeValue(WITHIN_CONDITION));
    invertWithinConstraint = getBooleanValue(element, NEGATE_WITHIN_CONDITION, false);

    partOfSearchResults = getBooleanValue(element, TARGET, false);

    contextConstraint = StringUtil.notNullize(element.getAttributeValue(CONTEXT));

    for (Attribute attribute : element.getAttributes()) {
      final String mangledName = attribute.getName();
      if (!StringUtil.startsWith(mangledName, "_")) {
        continue;
      }
      final String name = mangledName.substring(1);
      if (!isValidConstraintName(name)) {
        throw new InvalidDataException();
      }
      if (additionalConstraints == null) {
        additionalConstraints = new HashMap<>();
      }
      additionalConstraints.put(name, attribute.getValue());
    }
  }

  public static boolean getBooleanValue(Element element, String attributeName, boolean defaultValue) {
    final Attribute attribute = element.getAttribute(attributeName);
    if (attribute != null) {
      try {
        return attribute.getBooleanValue();
      }
      catch (DataConversionException ignored) {}
    }
    return defaultValue;
  }

  public static int getIntValue(Element element, String attributeName, int defaultValue) {
    final Attribute attribute = element.getAttribute(attributeName);
    if (attribute != null) {
      try {
        return attribute.getIntValue();
      }
      catch (DataConversionException ignored) {
      }
    }
    return defaultValue;
  }

  @Override
  public void writeExternal(Element element) {
    super.writeExternal(element);

    if (!regExp.isEmpty()) element.setAttribute(REGEXP, regExp);
    if (!nameOfExprType.isEmpty()) element.setAttribute(NAME_OF_EXPRTYPE, nameOfExprType);
    if (!referenceConstraint.isEmpty()) element.setAttribute(REFERENCE_CONDITION, referenceConstraint);
    if (!nameOfFormalArgType.isEmpty()) element.setAttribute(NAME_OF_FORMALTYPE, nameOfFormalArgType);

    if (withinHierarchy) element.setAttribute(WITHIN_HIERARCHY, TRUE);
    if (exprTypeWithinHierarchy) element.setAttribute(EXPRTYPE_WITHIN_HIERARCHY, TRUE);
    if (formalArgTypeWithinHierarchy) element.setAttribute(FORMALTYPE_WITHIN_HIERARCHY, TRUE);

    if (minCount != 1) element.setAttribute(MIN_OCCURS, String.valueOf(minCount));
    if (maxCount != 1) element.setAttribute(MAX_OCCURS, String.valueOf(maxCount));
    if (partOfSearchResults) element.setAttribute(TARGET, TRUE);

    if (invertRegExp) element.setAttribute(NEGATE_NAME_CONDITION, TRUE);
    if (invertExprType) element.setAttribute(NEGATE_EXPRTYPE_CONDITION, TRUE);
    if (invertFormalType) element.setAttribute(NEGATE_FORMALTYPE_CONDITION, TRUE);
    if (invertReference) element.setAttribute(NEGATE_REFERENCE_CONDITION, TRUE);

    if (wholeWordsOnly) element.setAttribute(WHOLE_WORDS_ONLY, TRUE);
    if (invertContainsConstraint) element.setAttribute(NEGATE_CONTAINS_CONDITION, TRUE);
    if (invertWithinConstraint) element.setAttribute(NEGATE_WITHIN_CONDITION, TRUE);
    element.setAttribute(WITHIN_CONDITION, withinConstraint);
    element.setAttribute(CONTAINS_CONDITION, containsConstraint);

    if (!contextConstraint.isEmpty()) element.setAttribute(CONTEXT, contextConstraint);

    if (additionalConstraints != null && !additionalConstraints.isEmpty()) {
      List<String> list = ContainerUtil.sorted(additionalConstraints.keySet());
      for (String key : list) {
        final String value = additionalConstraints.get(key);
        if (value != null) {
          element.setAttribute('_' + key, value);
        }
      }
    }
  }

  public @NotNull String getWithinConstraint() {
    return withinConstraint;
  }

  public void setWithinConstraint(@NotNull String withinConstraint) {
    this.withinConstraint = withinConstraint;
  }

  public @NotNull String getContainsConstraint() {
    return containsConstraint;
  }

  public void setContainsConstraint(@NotNull String containsConstraint) {
    this.containsConstraint = containsConstraint;
  }

  public boolean isInvertContainsConstraint() {
    return invertContainsConstraint;
  }

  public void setInvertContainsConstraint(boolean invertContainsConstraint) {
    this.invertContainsConstraint = invertContainsConstraint;
  }

  public boolean isInvertWithinConstraint() {
    return invertWithinConstraint;
  }

  public void setInvertWithinConstraint(boolean invertWithinConstraint) {
    this.invertWithinConstraint = invertWithinConstraint;
  }
}
