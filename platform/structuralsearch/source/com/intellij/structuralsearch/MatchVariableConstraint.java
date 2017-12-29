// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import org.jdom.Element;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Mossienko
 */
public class MatchVariableConstraint extends NamedScriptableDefinition {
  private String regExp = "";
  private boolean invertRegExp;
  private boolean withinHierarchy;
  private boolean strictlyWithinHierarchy;
  private boolean wholeWordsOnly;
  private int minCount = 1;
  private int maxCount = 1;
  private boolean greedy = true;
  private boolean invertReference;
  private String referenceConstraint = "";
  private boolean partOfSearchResults;
  private String nameOfExprType = "";
  private boolean invertExprType;
  private boolean exprTypeWithinHierarchy;

  private String nameOfFormalArgType = "";
  private boolean invertFormalType;
  private boolean formalArgTypeWithinHierarchy;

  private String withinConstraint = "";
  private String containsConstraint = "";
  private boolean invertContainsConstraint;
  private boolean invertWithinConstraint;
  private final boolean artificial;

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

  @NonNls private static final String WHOLE_WORDS_ONLY = "wholeWordsOnly";
  @NonNls private static final String TRUE = Boolean.TRUE.toString();

  public MatchVariableConstraint() { this(false); }
  public MatchVariableConstraint(boolean _artificial) { artificial = _artificial; }

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
    invertExprType = constraint.invertExprType;
    exprTypeWithinHierarchy = constraint.exprTypeWithinHierarchy;
    nameOfFormalArgType = constraint.nameOfFormalArgType;
    invertFormalType = constraint.invertFormalType;
    formalArgTypeWithinHierarchy = constraint.formalArgTypeWithinHierarchy;
    withinConstraint = constraint.withinConstraint;
    containsConstraint = constraint.containsConstraint;
    invertContainsConstraint = constraint.invertContainsConstraint;
    invertWithinConstraint = constraint.invertWithinConstraint;
    artificial = constraint.artificial;
  }

  @Override
  public MatchVariableConstraint copy() {
    return new MatchVariableConstraint(this);
  }

  public boolean isGreedy() {
    return greedy;
  }

  public void setGreedy(boolean greedy) {
    this.greedy = greedy;
  }

  public String getRegExp() {
    return regExp;
  }

  public void setRegExp(String regExp) {
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

  public String getReferenceConstraint() {
    return referenceConstraint;
  }

  public void setReferenceConstraint(String nameOfReferenceVar) {
    this.referenceConstraint = nameOfReferenceVar;
  }

  public boolean isStrictlyWithinHierarchy() {
    return strictlyWithinHierarchy;
  }

  public void setStrictlyWithinHierarchy(boolean strictlyWithinHierarchy) {
    this.strictlyWithinHierarchy = strictlyWithinHierarchy;
  }

  public String getNameOfExprType() {
    return nameOfExprType;
  }

  public void setNameOfExprType(String nameOfExprType) {
    this.nameOfExprType = nameOfExprType;
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

  public String getNameOfFormalArgType() {
    return nameOfFormalArgType;
  }

  public void setNameOfFormalArgType(String nameOfFormalArgType) {
    this.nameOfFormalArgType = nameOfFormalArgType;
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
    if (!nameOfFormalArgType.equals(matchVariableConstraint.nameOfFormalArgType)) return false;
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
    result = 29 * result + (invertExprType ? 1 : 0);
    result = 29 * result + (exprTypeWithinHierarchy ? 1 : 0);
    result = 29 * result + nameOfFormalArgType.hashCode();
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

    Attribute attribute = element.getAttribute(REGEXP);
    if (attribute != null) {
      regExp = attribute.getValue();
    }
    withinHierarchy = readBoolean(element, WITHIN_HIERARCHY);
    invertRegExp = readBoolean(element, NEGATE_NAME_CONDITION);
    wholeWordsOnly = readBoolean(element, WHOLE_WORDS_ONLY);

    attribute = element.getAttribute(NAME_OF_EXPRTYPE);
    if (attribute != null) {
      nameOfExprType = attribute.getValue();
    }
    exprTypeWithinHierarchy = readBoolean(element, EXPRTYPE_WITHIN_HIERARCHY);
    invertExprType = readBoolean(element, NEGATE_EXPRTYPE_CONDITION);


    attribute = element.getAttribute(NAME_OF_FORMALTYPE);
    if (attribute != null) {
      nameOfFormalArgType = attribute.getValue();
    }
    formalArgTypeWithinHierarchy = readBoolean(element, FORMALTYPE_WITHIN_HIERARCHY);
    invertFormalType = readBoolean(element, NEGATE_FORMALTYPE_CONDITION);

    attribute = element.getAttribute(MIN_OCCURS);
    if (attribute != null) {
      try {
        minCount = attribute.getIntValue();
      }
      catch (DataConversionException ignored) {
      }
    }

    attribute = element.getAttribute(MAX_OCCURS);
    if (attribute != null) {
      try {
        maxCount = attribute.getIntValue();
      }
      catch (DataConversionException ignored) {
      }
    }

    attribute = element.getAttribute(REFERENCE_CONDITION);
    if (attribute != null) referenceConstraint = attribute.getValue();
    invertReference = readBoolean(element, NEGATE_REFERENCE_CONDITION);

    attribute = element.getAttribute(CONTAINS_CONDITION);
    if (attribute != null) containsConstraint = attribute.getValue();
    invertContainsConstraint = readBoolean(element, NEGATE_CONTAINS_CONDITION);

    attribute = element.getAttribute(WITHIN_CONDITION);
    if (attribute != null) withinConstraint = attribute.getValue();
    invertWithinConstraint = readBoolean(element, NEGATE_WITHIN_CONDITION);

    partOfSearchResults = readBoolean(element, TARGET);
  }

  private static boolean readBoolean(Element element, String attributeName) {
    final Attribute attribute = element.getAttribute(attributeName);
    if (attribute != null) {
      try {
        return attribute.getBooleanValue();
      }
      catch (DataConversionException ignored) {}
    }
    return false;
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

    if (minCount != 1) element.setAttribute(MIN_OCCURS,String.valueOf(minCount));
    if (maxCount != 1) element.setAttribute(MAX_OCCURS,String.valueOf(maxCount));
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

  public String getWithinConstraint() {
    return withinConstraint;
  }

  public void setWithinConstraint(final String withinConstraint) {
    this.withinConstraint = withinConstraint;
  }

  public String getContainsConstraint() {
    return containsConstraint;
  }

  public void setContainsConstraint(final String containsConstraint) {
    this.containsConstraint = containsConstraint;
  }

  public boolean isInvertContainsConstraint() {
    return invertContainsConstraint;
  }

  public void setInvertContainsConstraint(final boolean invertContainsConstraint) {
    this.invertContainsConstraint = invertContainsConstraint;
  }

  public boolean isInvertWithinConstraint() {
    return invertWithinConstraint;
  }

  public void setInvertWithinConstraint(final boolean invertWithinConstraint) {
    this.invertWithinConstraint = invertWithinConstraint;
  }

  public boolean isArtificial() {
    return artificial;
  }
}
