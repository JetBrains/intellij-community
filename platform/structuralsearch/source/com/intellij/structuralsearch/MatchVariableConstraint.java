// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Maxim.Mossienko
 */
public class MatchVariableConstraint extends NamedScriptableDefinition {
  private static final Pattern VALID_CONSTRAINT_NAME = Pattern.compile("[a-z][A-Za-z\\d]*");

  private Map<String, String> additionalConstraints;

  @NotNull
  private String regExp = "";
  private boolean invertRegExp;
  private boolean withinHierarchy;
  private boolean strictlyWithinHierarchy;
  private boolean wholeWordsOnly;
  private int minCount = 1;
  private int maxCount = 1;
  private boolean greedy = true;
  private boolean invertReference;
  @NotNull
  private String referenceConstraint = "";
  private boolean partOfSearchResults;
  @NotNull
  private String nameOfExprType = "";
  @NotNull
  private String expressionTypes = "";
  private boolean invertExprType;
  private boolean exprTypeWithinHierarchy;

  @NotNull
  private String nameOfFormalArgType = "";
  @NotNull
  private String expectedTypes = "";
  private boolean invertFormalType;
  private boolean formalArgTypeWithinHierarchy;

  @NotNull
  private String withinConstraint = "";
  @NotNull
  private String containsConstraint = "";
  private boolean invertContainsConstraint;
  private boolean invertWithinConstraint;

  @NotNull private String contextConstraint = "";

  @NonNls private static final String REFERENCE_CONDITION = "reference";
  @NonNls private static final String NAME_OF_EXPRTYPE = "nameOfExprType";
  @NonNls private static final String NAME_OF_FORMALTYPE = "nameOfFormalType";
  @NonNls private static final String REGEXP = "regexp";
  @NonNls private static final String EXPRTYPE_WITHIN_HIERARCHY = "exprTypeWithinHierarchy";
  @NonNls private static final String FORMALTYPE_WITHIN_HIERARCHY = "formalTypeWithinHierarchy";

  @NonNls private static final String WITHIN_HIERARCHY = "withinHierarchy";
  @NonNls private static final String MAX_OCCURS = "maxCount";
  @NonNls private static final String MIN_OCCURS = "minCount";

  @NonNls private static final String NEGATE_NAME_CONDITION = "negateName";
  @NonNls private static final String NEGATE_EXPRTYPE_CONDITION = "negateExprType";
  @NonNls private static final String NEGATE_FORMALTYPE_CONDITION = "negateFormalType";
  @NonNls private static final String NEGATE_CONTAINS_CONDITION = "negateContains";
  @NonNls private static final String NEGATE_WITHIN_CONDITION = "negateWithin";
  @NonNls private static final String NEGATE_REFERENCE_CONDITION = "negateReference";
  @NonNls private static final String WITHIN_CONDITION = "within";
  @NonNls private static final String CONTAINS_CONDITION = "contains";
  @NonNls private static final String TARGET = "target";
  @NonNls private static final String CONTEXT = "context";

  @NonNls private static final String WHOLE_WORDS_ONLY = "wholeWordsOnly";
  @NonNls private static final String TRUE = Boolean.TRUE.toString();

  private static final Set<String> ALL_ATTRIBUTES = ContainerUtil.set(
    REFERENCE_CONDITION, NAME_OF_EXPRTYPE, NAME_OF_FORMALTYPE, REGEXP, EXPRTYPE_WITHIN_HIERARCHY, FORMALTYPE_WITHIN_HIERARCHY,
    WITHIN_HIERARCHY, MAX_OCCURS, MIN_OCCURS, NEGATE_NAME_CONDITION, NEGATE_EXPRTYPE_CONDITION, NEGATE_FORMALTYPE_CONDITION,
    NEGATE_CONTAINS_CONDITION, NEGATE_WITHIN_CONDITION, NEGATE_REFERENCE_CONDITION, WITHIN_CONDITION, CONTAINS_CONDITION,
    TARGET, CONTEXT, WHOLE_WORDS_ONLY
  );

  public MatchVariableConstraint() {}

  public MatchVariableConstraint(String name) {
    setName(name);
  }

  MatchVariableConstraint(MatchVariableConstraint constraint) {
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
  }

  @Override
  public MatchVariableConstraint copy() {
    return new MatchVariableConstraint(this);
  }

  @NotNull
  static String convertRegExpTypeToTypeString(@NotNull String regexp) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0, length = regexp.length(); i < length; i++) {
      final int c = regexp.codePointAt(i);
      if (c == '.') {
        if (i == length - 1 || !StructuralSearchUtil.isRegExpMetaChar(regexp.codePointAt(i + 1))) {
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
      else if (StructuralSearchUtil.isRegExpMetaChar(c)) {
        return ""; // can't convert
      }
      else {
        result.appendCodePoint(c);
      }
    }
    return result.toString();
  }

  @NotNull
  static String convertTypeStringToRegExp(@NotNull String typeString) {
    final StringBuilder result = new StringBuilder();
    for (String type : StringUtil.split(typeString, "|")) {
      if (result.length() > 0) {
        result.append('|');
      }
      StructuralSearchUtil.shieldRegExpMetaChars(type.trim(), result);
    }
    return result.toString();
  }

  public boolean isGreedy() {
    return greedy;
  }

  public void setGreedy(boolean greedy) {
    this.greedy = greedy;
  }

  @NotNull
  public String getRegExp() {
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

  @NotNull
  public String getReferenceConstraint() {
    return referenceConstraint;
  }

  public void setReferenceConstraint(@NotNull String nameOfReferenceVar) {
    this.referenceConstraint = nameOfReferenceVar;
  }

  public boolean isStrictlyWithinHierarchy() {
    return strictlyWithinHierarchy;
  }

  public void setStrictlyWithinHierarchy(boolean strictlyWithinHierarchy) {
    this.strictlyWithinHierarchy = strictlyWithinHierarchy;
  }

  @NotNull
  public String getNameOfExprType() {
    return nameOfExprType;
  }

  @NotNull
  public String getExpressionTypes() {
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

  @NotNull
  public String getNameOfFormalArgType() {
    return nameOfFormalArgType;
  }

  @NotNull
  public String getExpectedTypes() {
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

  @NotNull
  public String getContextConstraint() {
    return contextConstraint;
  }

  public void setContextConstraint(@NotNull String contextConstraint) {
    this.contextConstraint = contextConstraint;
  }

  private static boolean isValidConstraintName(String name) {
    return !ALL_ATTRIBUTES.contains(name) && VALID_CONSTRAINT_NAME.matcher(name).matches();
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
    if (additionalConstraints == null) {
      return null;
    }
    return additionalConstraints.get(name);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchVariableConstraint)) return false;
    if (!(super.equals(o))) return false;

    final MatchVariableConstraint other = (MatchVariableConstraint)o;

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
    } else if (other.additionalConstraints != null) {
      return false;
    }
    return true;
  }

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
      final String name = attribute.getName();
      if (isValidConstraintName(name)) {
        if (additionalConstraints == null) {
          additionalConstraints = new HashMap<>();
        }
        additionalConstraints.put(name, attribute.getValue());
      }
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
      final SmartList<String> list = new SmartList<>(additionalConstraints.keySet());
      Collections.sort(list);
      for (String key : list) {
        final String value = additionalConstraints.get(key);
        if (value != null) {
          element.setAttribute(key, value);
        }
      }
    }
  }

  @NotNull
  public String getWithinConstraint() {
    return withinConstraint;
  }

  public void setWithinConstraint(@NotNull String withinConstraint) {
    this.withinConstraint = withinConstraint;
  }

  @NotNull
  public String getContainsConstraint() {
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
