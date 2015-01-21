package com.intellij.structuralsearch;

import org.jdom.Element;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Mossienko
 * Date: Mar 19, 2004
 * Time: 5:36:32 PM
 */
public class MatchVariableConstraint extends NamedScriptableDefinition {
  private String regExp = "";
  private boolean invertRegExp;
  private boolean withinHierarchy;
  private boolean strictlyWithinHierarchy;
  private boolean wholeWordsOnly;
  private int minCount = 1;
  private int maxCount = 1;
  private boolean readAccess;
  private boolean invertReadAccess;
  private boolean writeAccess;
  private boolean invertWriteAccess;
  private boolean greedy = true;
  private boolean reference;
  private boolean invertReference;
  private String nameOfReferenceVar = "";
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

  @NonNls private static final String NAME_OF_REFEENCE_VAR = "nameOfReferenceVar";
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
  @NonNls private static final String NEGATE_READ_CONDITION = "negateRead";
  @NonNls private static final String NEGATE_WRITE_CONDITION = "negateWrite";
  @NonNls private static final String NEGATE_CONTAINS_CONDITION = "negateContains";
  @NonNls private static final String NEGATE_WITHIN_CONDITION = "negateWithin";
  @NonNls private static final String WITHIN_CONDITION = "within";
  @NonNls private static final String CONTAINS_CONDITION = "contains";
  @NonNls private static final String READ = "readAccess";
  @NonNls private static final String WRITE = "writeAccess";
  @NonNls private static final String TARGET = "target";

  @NonNls private static final String WHOLE_WORDS_ONLY = "wholeWordsOnly";
  @NonNls private static final String TRUE = Boolean.TRUE.toString();

  public MatchVariableConstraint() { this(false); }
  public MatchVariableConstraint(boolean _artificial) { artificial = _artificial; }

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

  public boolean isReadAccess() {
    return readAccess;
  }

  public void setReadAccess(boolean readAccess) {
    this.readAccess = readAccess;
  }

  public boolean isInvertReadAccess() {
    return invertReadAccess;
  }

  public void setInvertReadAccess(boolean invertReadAccess) {
    this.invertReadAccess = invertReadAccess;
  }

  public boolean isWriteAccess() {
    return writeAccess;
  }

  public void setWriteAccess(boolean writeAccess) {
    this.writeAccess = writeAccess;
  }

  public boolean isInvertWriteAccess() {
    return invertWriteAccess;
  }

  public void setInvertWriteAccess(boolean invertWriteAccess) {
    this.invertWriteAccess = invertWriteAccess;
  }

  public boolean isPartOfSearchResults() {
    return partOfSearchResults;
  }

  public void setPartOfSearchResults(boolean partOfSearchResults) {
    this.partOfSearchResults = partOfSearchResults;
  }

  public boolean isReference() {
    return reference;
  }

  public void setReference(boolean reference) {
    this.reference = reference;
  }

  public boolean isInvertReference() {
    return invertReference;
  }

  public void setInvertReference(boolean invertReference) {
    this.invertReference = invertReference;
  }

  public String getNameOfReferenceVar() {
    return nameOfReferenceVar;
  }

  public void setNameOfReferenceVar(String nameOfReferenceVar) {
    this.nameOfReferenceVar = nameOfReferenceVar;
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
    if (invertReadAccess != matchVariableConstraint.invertReadAccess) return false;
    if (invertReference != matchVariableConstraint.invertReference) return false;
    if (invertRegExp != matchVariableConstraint.invertRegExp) return false;
    if (invertWriteAccess != matchVariableConstraint.invertWriteAccess) return false;
    if (maxCount != matchVariableConstraint.maxCount) return false;
    if (minCount != matchVariableConstraint.minCount) return false;
    if (partOfSearchResults != matchVariableConstraint.partOfSearchResults) return false;
    if (readAccess != matchVariableConstraint.readAccess) return false;
    if (reference != matchVariableConstraint.reference) return false;
    if (strictlyWithinHierarchy != matchVariableConstraint.strictlyWithinHierarchy) return false;
    if (wholeWordsOnly != matchVariableConstraint.wholeWordsOnly) return false;
    if (withinHierarchy != matchVariableConstraint.withinHierarchy) return false;
    if (writeAccess != matchVariableConstraint.writeAccess) return false;
    if (!nameOfExprType.equals(matchVariableConstraint.nameOfExprType)) return false;
    if (!nameOfFormalArgType.equals(matchVariableConstraint.nameOfFormalArgType)) return false;
    if (!nameOfReferenceVar.equals(matchVariableConstraint.nameOfReferenceVar)) return false;
    if (!regExp.equals(matchVariableConstraint.regExp)) return false;
    if (!withinConstraint.equals(matchVariableConstraint.withinConstraint)) return false;
    if (!containsConstraint.equals(matchVariableConstraint.containsConstraint)) return false;
    if (invertWithinConstraint != matchVariableConstraint.invertWithinConstraint) return false;
    if (invertContainsConstraint != matchVariableConstraint.invertContainsConstraint) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = super.hashCode();
    result = 29 * result + regExp.hashCode();
    result = 29 * result + (invertRegExp ? 1 : 0);
    result = 29 * result + (withinHierarchy ? 1 : 0);
    result = 29 * result + (strictlyWithinHierarchy ? 1 : 0);
    result = 29 * result + (wholeWordsOnly ? 1 : 0);
    result = 29 * result + minCount;
    result = 29 * result + maxCount;
    result = 29 * result + (readAccess ? 1 : 0);
    result = 29 * result + (invertReadAccess ? 1 : 0);
    result = 29 * result + (writeAccess ? 1 : 0);
    result = 29 * result + (invertWriteAccess ? 1 : 0);
    result = 29 * result + (greedy ? 1 : 0);
    result = 29 * result + (reference ? 1 : 0);
    result = 29 * result + (invertReference ? 1 : 0);
    result = 29 * result + nameOfReferenceVar.hashCode();
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

  public void readExternal(Element element) {
    super.readExternal(element);
    Attribute attribute;

    attribute = element.getAttribute(REGEXP);
    if (attribute != null) {
      regExp = attribute.getValue();
    }

    attribute = element.getAttribute(NAME_OF_EXPRTYPE);
    if (attribute != null) {
      nameOfExprType = attribute.getValue();
    }

    attribute = element.getAttribute(NAME_OF_FORMALTYPE);
    if (attribute != null) {
      nameOfFormalArgType = attribute.getValue();
    }

    attribute = element.getAttribute(NAME_OF_REFEENCE_VAR);
    if (attribute != null) {
      nameOfReferenceVar = attribute.getValue();
    }

    attribute = element.getAttribute(WITHIN_HIERARCHY);
    if (attribute != null) {
      try {
        withinHierarchy = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(EXPRTYPE_WITHIN_HIERARCHY);
    if (attribute != null) {
      try {
        exprTypeWithinHierarchy = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(FORMALTYPE_WITHIN_HIERARCHY);
    if (attribute != null) {
      try {
        formalArgTypeWithinHierarchy = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(NEGATE_NAME_CONDITION);
    if (attribute != null) {
      try {
        invertRegExp = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(NEGATE_EXPRTYPE_CONDITION);
    if (attribute != null) {
      try {
        invertExprType = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(NEGATE_FORMALTYPE_CONDITION);
    if (attribute != null) {
      try {
        invertFormalType = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(NEGATE_READ_CONDITION);
    if (attribute != null) {
      try {
        invertReadAccess = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(NEGATE_WRITE_CONDITION);
    if (attribute != null) {
      try {
        invertWriteAccess = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(READ);
    if (attribute != null) {
      try {
        readAccess = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(WRITE);
    if (attribute != null) {
      try {
        writeAccess = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(TARGET);
    if (attribute != null) {
      try {
        partOfSearchResults = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(MIN_OCCURS);
    if (attribute != null) {
      try {
        minCount = attribute.getIntValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(MAX_OCCURS);
    if (attribute != null) {
      try {
        maxCount = attribute.getIntValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(WHOLE_WORDS_ONLY);
    if (attribute != null) {
      try {
        wholeWordsOnly = attribute.getBooleanValue();
      }
      catch (DataConversionException ex) {
      }
    }

    attribute = element.getAttribute(NEGATE_WITHIN_CONDITION);
    if (attribute != null) {
      try {
        invertWithinConstraint = attribute.getBooleanValue();
      } catch (DataConversionException ex) {}
    }

    attribute = element.getAttribute(NEGATE_CONTAINS_CONDITION);
    if (attribute != null) {
      try {
        invertContainsConstraint = attribute.getBooleanValue();
      } catch (DataConversionException ex) {}
    }

    attribute = element.getAttribute(CONTAINS_CONDITION);
    if(attribute != null) containsConstraint = attribute.getValue();

    attribute = element.getAttribute(WITHIN_CONDITION);
    if(attribute != null) withinConstraint = attribute.getValue();
  }

  public void writeExternal(Element element) {
    super.writeExternal(element);

    if (regExp.length() > 0) element.setAttribute(REGEXP,regExp);
    if (nameOfExprType.length() > 0) element.setAttribute(NAME_OF_EXPRTYPE,nameOfExprType);
    if (nameOfReferenceVar.length() > 0) element.setAttribute(NAME_OF_REFEENCE_VAR,nameOfReferenceVar);
    if (nameOfFormalArgType.length() > 0) element.setAttribute(NAME_OF_FORMALTYPE,nameOfFormalArgType);


    if (withinHierarchy) element.setAttribute(WITHIN_HIERARCHY,TRUE);
    if (exprTypeWithinHierarchy) element.setAttribute(EXPRTYPE_WITHIN_HIERARCHY,TRUE);
    if (formalArgTypeWithinHierarchy) element.setAttribute(FORMALTYPE_WITHIN_HIERARCHY,TRUE);

    if (minCount!=1) element.setAttribute(MIN_OCCURS,String.valueOf(minCount));
    if (maxCount!=1) element.setAttribute(MAX_OCCURS,String.valueOf(maxCount));
    if (partOfSearchResults) element.setAttribute(TARGET,TRUE);
    if (readAccess) element.setAttribute(READ,TRUE);
    if (writeAccess) element.setAttribute(WRITE,TRUE);

    if (invertRegExp) element.setAttribute(NEGATE_NAME_CONDITION,TRUE);
    if (invertExprType) element.setAttribute(NEGATE_EXPRTYPE_CONDITION,TRUE);
    if (invertFormalType) element.setAttribute(NEGATE_FORMALTYPE_CONDITION,TRUE);
    if (invertReadAccess) element.setAttribute(NEGATE_READ_CONDITION,TRUE);
    if (invertWriteAccess) element.setAttribute(NEGATE_WRITE_CONDITION,TRUE);

    if (wholeWordsOnly) element.setAttribute(WHOLE_WORDS_ONLY,TRUE);
    if (invertContainsConstraint) element.setAttribute(NEGATE_CONTAINS_CONDITION,TRUE);
    if (invertWithinConstraint) element.setAttribute(NEGATE_WITHIN_CONDITION,TRUE);
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
