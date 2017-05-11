/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.impl.matcher.compiler.StringToConstraintsTransformer;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MatchOptions implements JDOMExternalizable {
  @NonNls private static final String TEXT_ATTRIBUTE_NAME = "text";

  private boolean looseMatching;
  private boolean recursiveSearch;
  private boolean caseSensitiveMatch;
  private boolean resultIsContextMatch = false;
  private FileType myFileType = null;
  private Language myDialect = null;

  private SearchScope scope;
  private String pattern = "";
  @Nullable private Map<String,MatchVariableConstraint> variableConstraints;

  private String myPatternContext;

  @NonNls private static final String LOOSE_MATCHING_ATTRIBUTE_NAME = "loose";
  @NonNls private static final String RECURSIVE_ATTRIBUTE_NAME = "recursive";
  @NonNls private static final String CASESENSITIVE_ATTRIBUTE_NAME = "caseInsensitive";
  @NonNls private static final String CONSTRAINT_TAG_NAME = "constraint";
  @NonNls private static final String FILE_TYPE_ATTR_NAME = "type";
  @NonNls private static final String DIALECT_ATTR_NAME = "dialect";
  @NonNls public static final String INSTANCE_MODIFIER_NAME = "Instance";
  @NonNls public static final String MODIFIER_ANNOTATION_NAME = "Modifier";

  public void addVariableConstraint(MatchVariableConstraint constraint) {
    if (variableConstraints==null) {
      variableConstraints = new LinkedHashMap<>();
    }
    variableConstraints.put( constraint.getName(), constraint );
  }

  public boolean hasVariableConstraints() {
    return variableConstraints!=null;
  }

  public void clearVariableConstraints() {
    variableConstraints=null;
  }

  public void retainVariableConstraints(Collection<String> names) {
    if (variableConstraints == null || variableConstraints.isEmpty()) {
      return;
    }
    final THashSet<String> nameSet = new THashSet<>(names);
    for (final Iterator<String> iterator = variableConstraints.keySet().iterator(); iterator.hasNext(); ) {
      final String key = iterator.next();
      if (!nameSet.contains(key)) {
        iterator.remove();
      }
    }
  }

  public MatchVariableConstraint getVariableConstraint(String name) {
    if (variableConstraints!=null) {
      return variableConstraints.get(name);
    }
    return null;
  }

  public Set<String> getVariableConstraintNames() {
    if (variableConstraints==null) return Collections.emptySet();
    return Collections.unmodifiableSet(variableConstraints.keySet());
  }

  public void setCaseSensitiveMatch(boolean caseSensitiveMatch) {
    this.caseSensitiveMatch = caseSensitiveMatch;
  }

  public boolean isCaseSensitiveMatch() {
    return caseSensitiveMatch;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  public void setSearchPattern(String text) {
    pattern = text;
  }

  public String getSearchPattern() {
    return pattern;
  }

  public void fillSearchCriteria(String criteria) {
    StringToConstraintsTransformer.transformCriteria(criteria, this);
  }

  public boolean isResultIsContextMatch() {
    return resultIsContextMatch;
  }

  public void setResultIsContextMatch(boolean resultIsContextMatch) {
    this.resultIsContextMatch = resultIsContextMatch;
  }

  public SearchScope getScope() {
    return scope;
  }

  public void setScope(SearchScope scope) {
    this.scope = scope;
  }

  public void writeExternal(Element element) {
    element.setAttribute(TEXT_ATTRIBUTE_NAME, pattern);
    if (!looseMatching) {
      element.setAttribute(LOOSE_MATCHING_ATTRIBUTE_NAME, String.valueOf(false));
    }
    element.setAttribute(RECURSIVE_ATTRIBUTE_NAME,String.valueOf(recursiveSearch));
    element.setAttribute(CASESENSITIVE_ATTRIBUTE_NAME,String.valueOf(caseSensitiveMatch));

    //@TODO serialize scope!

    if (myFileType != null) {
      element.setAttribute(FILE_TYPE_ATTR_NAME, myFileType.getName());
    }

    if (myDialect != null) {
      element.setAttribute(DIALECT_ATTR_NAME, myDialect.getID());
    }

    if (variableConstraints!=null) {
      for (final MatchVariableConstraint matchVariableConstraint : variableConstraints.values()) {
        if (matchVariableConstraint.isArtificial()) continue;
        final Element infoElement = new Element(CONSTRAINT_TAG_NAME);
        element.addContent(infoElement);
        matchVariableConstraint.writeExternal(infoElement);
      }
    }
  }

  public void readExternal(Element element) {
    pattern = element.getAttribute(TEXT_ATTRIBUTE_NAME).getValue();

    Attribute attr = element.getAttribute(LOOSE_MATCHING_ATTRIBUTE_NAME);
    if (attr != null) {
      try {
        looseMatching = attr.getBooleanValue();
      } catch (DataConversionException ignored) {}
    } else {
      looseMatching = true; // default is loose
    }

    attr = element.getAttribute(RECURSIVE_ATTRIBUTE_NAME);
    if (attr != null) {
      try {
        recursiveSearch = attr.getBooleanValue();
      } catch(DataConversionException ignored) {}
    }

    attr = element.getAttribute(CASESENSITIVE_ATTRIBUTE_NAME);
    if (attr!=null) {
      try {
        caseSensitiveMatch = attr.getBooleanValue();
      } catch(DataConversionException ignored) {}
    }

    attr = element.getAttribute(FILE_TYPE_ATTR_NAME);
    if (attr!=null) {
      String value = attr.getValue();
      myFileType = getFileTypeByName(value);
    }

    attr = element.getAttribute(DIALECT_ATTR_NAME);
    if (attr != null) {
      myDialect = Language.findLanguageByID(attr.getValue());
    }

    // @TODO deserialize scope

    List<Element> elements = element.getChildren(CONSTRAINT_TAG_NAME);
    if (elements!=null && !elements.isEmpty()) {
      for (final Element element1 : elements) {
        final MatchVariableConstraint constraint = new MatchVariableConstraint();
        constraint.readExternal(element1);
        addVariableConstraint(constraint);
      }
    }
  }

  private static FileType getFileTypeByName(String value) {
    if (value != null) {
      for (FileType type : StructuralSearchUtil.getSuitableFileTypes()) {
        if (value.equals(type.getName())) {
          return type;
        }
      }
    }

    return StructuralSearchUtil.getDefaultFileType();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchOptions)) return false;

    final MatchOptions matchOptions = (MatchOptions)o;

    if (caseSensitiveMatch != matchOptions.caseSensitiveMatch) return false;
    //if (enableAutoIdentifySearchTarget != matchOptions.enableAutoIdentifySearchTarget) return false;
    if (looseMatching != matchOptions.looseMatching) return false;
    if (recursiveSearch != matchOptions.recursiveSearch) return false;
    // @TODO support scope

    if (pattern != null ? !pattern.equals(matchOptions.pattern) : matchOptions.pattern != null) return false;
    if (variableConstraints != null ? !variableConstraints.equals(matchOptions.variableConstraints) : matchOptions.variableConstraints !=
                                                                                                      null) {
      return false;
    }
    if (myFileType != matchOptions.myFileType) {
      return false;
    }

    if (myDialect != null ? !myDialect.equals(matchOptions.myDialect) : matchOptions.myDialect != null) {
      return false;
    }

    if (myPatternContext != null ? !myPatternContext.equals(matchOptions.myPatternContext) : matchOptions.myPatternContext != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result = (looseMatching ? 1 : 0);
    result = 29 * result + (recursiveSearch ? 1 : 0);
    result = 29 * result + (caseSensitiveMatch ? 1 : 0);
    // @TODO support scope
    result = 29 * result + (pattern != null ? pattern.hashCode() : 0);
    result = 29 * result + (variableConstraints != null ? variableConstraints.hashCode() : 0);
    if (myFileType != null) result = 29 * result + myFileType.hashCode();
    if (myDialect != null) result = 29 * result + myDialect.hashCode();
    return result;
  }

  public void setFileType(FileType fileType) {
    myFileType = fileType;
  }

  public FileType getFileType() {
    if (myFileType == null) {
      myFileType =  StructuralSearchUtil.getDefaultFileType();
    }
    return myFileType;
  }

  public Language getDialect() {
    return myDialect;
  }

  public void setDialect(Language dialect) {
    myDialect = dialect;
  }

  public String getPatternContext() {
    return myPatternContext;
  }

  public void setPatternContext(String patternContext) {
    myPatternContext = patternContext;
  }
}
