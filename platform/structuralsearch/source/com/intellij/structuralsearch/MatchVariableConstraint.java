// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class MatchVariableConstraint extends NamedScriptableDefinition {
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

  @NonNls private static final String REFERENCE_CONDITION = "reference";
  @NonNls private static final String NAME_OF_EXPRTYPE = "nameOfExprType";
  @NonNls private static final String EXPRESSION_TYPES = "expressionTypes";
  @NonNls private static final String NAME_OF_FORMALTYPE = "nameOfFormalType";
  @NonNls private static final String EXPECTED_TYPES = "exceptedTypes";
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

  @NonNls private static final String WHOLE_WORDS_ONLY = "wholeWordsOnly";
  @NonNls private static final String TRUE = Boolean.TRUE.toString();

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
    return Registry.is("ssr.use.regexp.to.specify.type") ? nameOfExprType : expressionTypes;
  }

  public void setNameOfExprType(@NotNull String nameOfExprType) {
    if (Registry.is("ssr.use.regexp.to.specify.type")) {
      this.nameOfExprType = nameOfExprType;
      this.expressionTypes = convertRegExpTypeToTypeString(nameOfExprType);
    }
    else {
      this.nameOfExprType = convertTypeStringToRegExp(nameOfExprType);
      this.expressionTypes = nameOfExprType;
    }
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
    return Registry.is("ssr.use.regexp.to.specify.type") ? nameOfFormalArgType : expectedTypes;
  }

  public void setNameOfFormalArgType(@NotNull String nameOfFormalArgType) {
    if (Registry.is("ssr.use.regexp.to.specify.type")) {
      this.nameOfFormalArgType = nameOfFormalArgType;
      this.expectedTypes = convertRegExpTypeToTypeString(nameOfFormalArgType);
    }
    else {
      this.nameOfFormalArgType = convertTypeStringToRegExp(nameOfFormalArgType);
      this.expectedTypes = nameOfFormalArgType;
    }
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

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchVariableConstraint)) return false;
    if (!(super.equals(o))) return false;

    final MatchVariableConstraint matchVariableConstraint = (MatchVariableConstraint)o;

    if (exprTypeWithinHierarchy != matchVariableConstraint.exprTypeWithinHierarchy) return false;
    if (formalArgTypeWithinHierarchy != matchVariableConstraint.formalArgTypeWithinHierarchy) return false;
    if (greedy != matchVariableConstraint.greedy) return false;
    if (invertExprType != matchVariableConstraint.invertExprType) return false;
    if (invertFormalType != matchVariableConstraint.invertFormalType) return false;
    if (invertReference != matchVariableConstraint.invertReference) return false;
    if (invertRegExp != matchVariableConstraint.invertRegExp) return false;
    if (maxCount != matchVariableConstraint.maxCount) return false;
    if (minCount != matchVariableConstraint.minCount) return false;
    if (partOfSearchResults != matchVariableConstraint.partOfSearchResults) return false;
    if (strictlyWithinHierarchy != matchVariableConstraint.strictlyWithinHierarchy) return false;
    if (wholeWordsOnly != matchVariableConstraint.wholeWordsOnly) return false;
    if (withinHierarchy != matchVariableConstraint.withinHierarchy) return false;
    if (!nameOfExprType.equals(matchVariableConstraint.nameOfExprType)) return false;
    if (!expressionTypes.equals(matchVariableConstraint.expressionTypes)) return false;
    if (!nameOfFormalArgType.equals(matchVariableConstraint.nameOfFormalArgType)) return false;
    if (!expectedTypes.equals(matchVariableConstraint.expectedTypes)) return false;
    if (!referenceConstraint.equals(matchVariableConstraint.referenceConstraint)) return false;
    if (!regExp.equals(matchVariableConstraint.regExp)) return false;
    if (!withinConstraint.equals(matchVariableConstraint.withinConstraint)) return false;
    if (!containsConstraint.equals(matchVariableConstraint.containsConstraint)) return false;
    if (invertWithinConstraint != matchVariableConstraint.invertWithinConstraint) return false;
    if (invertContainsConstraint != matchVariableConstraint.invertContainsConstraint) return false;

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
    
    if (invertContainsConstraint) result = 29 * result + 1;
    if (invertWithinConstraint) result = 29 * result + 1;
    return result;
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);

    regExp = StringUtil.notNullize(element.getAttributeValue(REGEXP));
    withinHierarchy = getBooleanValue(element, WITHIN_HIERARCHY, false);
    invertRegExp = getBooleanValue(element, NEGATE_NAME_CONDITION, false);
    wholeWordsOnly = getBooleanValue(element, WHOLE_WORDS_ONLY, false);

    expressionTypes = StringUtil.notNullize(element.getAttributeValue(EXPRESSION_TYPES));

    nameOfExprType = StringUtil.notNullize(element.getAttributeValue(NAME_OF_EXPRTYPE));
    if (expressionTypes.isEmpty() && !nameOfExprType.isEmpty()) {
      expressionTypes = convertRegExpTypeToTypeString(nameOfExprType);
    }

    exprTypeWithinHierarchy = getBooleanValue(element, EXPRTYPE_WITHIN_HIERARCHY, false);
    invertExprType = getBooleanValue(element, NEGATE_EXPRTYPE_CONDITION, false);
    expectedTypes = StringUtil.notNullize(element.getAttributeValue(EXPECTED_TYPES));

    nameOfFormalArgType = StringUtil.notNullize(element.getAttributeValue(NAME_OF_FORMALTYPE));
    if (expectedTypes.isEmpty() && !nameOfFormalArgType.isEmpty()) {
      expectedTypes = convertRegExpTypeToTypeString(nameOfFormalArgType);
    }

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
    if (!Registry.is("ssr.use.regexp.to.specify.type") && !expressionTypes.isEmpty() && !expressionTypes.equals(nameOfExprType)) {
      element.setAttribute(EXPRESSION_TYPES, expressionTypes);
    }
    if (!referenceConstraint.isEmpty()) element.setAttribute(REFERENCE_CONDITION, referenceConstraint);
    if (!nameOfFormalArgType.isEmpty()) element.setAttribute(NAME_OF_FORMALTYPE, nameOfFormalArgType);
    if (!Registry.is("ssr.use.regexp.to.specify.type") && !expectedTypes.isEmpty() && !expectedTypes.equals(nameOfFormalArgType)) {
      element.setAttribute(EXPECTED_TYPES, expectedTypes);
    }

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
