/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class GrMapType extends GrLiteralClassType {
  private static final PsiType[] RAW_PARAMETERS = new PsiType[]{null, null};
  
  private final Map<String, PsiType> myStringEntries;
  private final List<Pair<PsiType, PsiType>> myOtherEntries;
  @NonNls
  private static final String JAVA_UTIL_LINKED_HASH_MAP = "java.util.LinkedHashMap";
  private final String myJavaClassName;


  public GrMapType(GlobalSearchScope scope) {
    this(JavaPsiFacade.getInstance(scope.getProject()), scope, Collections.<String, PsiType>emptyMap(), Collections.<Pair<PsiType, PsiType>>emptyList(), LanguageLevel.JDK_1_5);
  }

  public GrMapType(JavaPsiFacade facade,
                   GlobalSearchScope scope,
                   Map<String, PsiType> stringEntries,
                   List<Pair<PsiType, PsiType>> otherEntries) {
    this(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }


  public GrMapType(JavaPsiFacade facade,
                   GlobalSearchScope scope,
                   Map<String, PsiType> stringEntries,
                   List<Pair<PsiType, PsiType>> otherEntries, LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);
    myStringEntries = stringEntries;
    myOtherEntries = otherEntries;

    myJavaClassName = facade.findClass(JAVA_UTIL_LINKED_HASH_MAP, scope) != null ? JAVA_UTIL_LINKED_HASH_MAP : CommonClassNames.JAVA_UTIL_MAP;
  }

  @Override
  protected String getJavaClassName() {
    return myJavaClassName;
  }

  public String getClassName() {
    return "Map";
  }

  public PsiType[] getAllKeyTypes() {
    Set<PsiType> result = new HashSet<PsiType>();
    if (!myStringEntries.isEmpty()) {
      result.add(PsiType.getJavaLangString(getPsiManager(), getResolveScope()));
    }
    for (Pair<PsiType, PsiType> entry : myOtherEntries) {
      result.add(entry.first);
    }
    result.remove(null);
    return result.toArray(new PsiType[result.size()]);
  }

  public PsiType[] getAllValueTypes() {
    Set<PsiType> result = new HashSet<PsiType>();
    result.addAll(myStringEntries.values());
    for (Pair<PsiType, PsiType> entry : myOtherEntries) {
      result.add(entry.second);
    }
    result.remove(null);
    return result.toArray(new PsiType[result.size()]);
  }

  @NotNull
  public PsiType[] getParameters() {
    final PsiType[] keyTypes = getAllKeyTypes();
    final PsiType[] valueTypes = getAllValueTypes();
    if (keyTypes.length == 0 && valueTypes.length == 0) {
      return RAW_PARAMETERS;
    }

    return new PsiType[]{getLeastUpperBound(keyTypes), getLeastUpperBound(valueTypes)};
  }

  public String getInternalCanonicalText() {
    List<String> components = new ArrayList<String>();
    for (String s : myStringEntries.keySet()) {
      components.add("'" + s + "':" + getInternalCanonicalText(myStringEntries.get(s)));
    }
    for (Pair<PsiType, PsiType> entry : myOtherEntries) {
      components.add(getInternalCanonicalText(entry.first) + ":" + getInternalCanonicalText(entry.second));
    }
    boolean tooMany = components.size() > 2;
    final List<String> theFirst = components.subList(0, Math.min(2, components.size()));
    return "[" + StringUtil.join(theFirst, ", ") + (tooMany ? ",..." : "") + "]";
  }

  public boolean isValid() {
    for (PsiType type : myStringEntries.values()) {
      if (type != null && !type.isValid()) {
        return false;
      }
    }
    for (Pair<PsiType, PsiType> entry : myOtherEntries) {
      if (entry.first != null && !entry.first.isValid()) {
        return false;
      }
      if (entry.second != null && !entry.second.isValid()) {
        return false;
      }
    }

    return true;
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    return new GrMapType(myFacade, getResolveScope(), myStringEntries, myOtherEntries, languageLevel);
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrMapType) {
      return myStringEntries.equals(((GrMapType)obj).myStringEntries) && myOtherEntries.equals(((GrMapType)obj).myOtherEntries);
    }
    return super.equals(obj);
  }

  @Nullable
  public PsiType getValueType(@NotNull String key) {
    return myStringEntries.get(key);
  }

  public boolean isAssignableFrom(@NotNull PsiType type) {
    return type instanceof GrMapType || myFacade.getElementFactory().createTypeFromText(getJavaClassName(), null).isAssignableFrom(type);
  }

}
