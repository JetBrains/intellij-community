/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.*;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP;

/**
 * @author peter
 */
public class GrMapType extends GrLiteralClassType {
  private final Map<String, PsiType> myStringEntries;
  private final List<Pair<PsiType, PsiType>> myOtherEntries;
  private final String myJavaClassName;

  private GrMapType(JavaPsiFacade facade,
                    GlobalSearchScope scope,
                    Map<String, PsiType> stringEntries,
                    List<Pair<PsiType, PsiType>> otherEntries,
                    LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);
    myStringEntries = stringEntries;
    myOtherEntries = otherEntries;

    myJavaClassName = facade.findClass(JAVA_UTIL_LINKED_HASH_MAP, scope) != null ? JAVA_UTIL_LINKED_HASH_MAP : JAVA_UTIL_MAP;
  }

  public GrMapType(@NotNull PsiElement context, GrNamedArgument[] args) {
    super(LanguageLevel.JDK_1_5, context.getResolveScope(), JavaPsiFacade.getInstance(context.getProject()));

    myJavaClassName = myFacade.findClass(JAVA_UTIL_LINKED_HASH_MAP, myScope) != null ? JAVA_UTIL_LINKED_HASH_MAP : JAVA_UTIL_MAP;

    myStringEntries = new HashMap<String, PsiType>();
    myOtherEntries = new ArrayList<Pair<PsiType, PsiType>>();

    for (GrNamedArgument arg : args) {
      GrArgumentLabel label = arg.getLabel();
      if (label == null) continue;

      GrExpression expression = arg.getExpression();
      if (expression == null || expression.getType() == null) continue;

      String labelName = label.getName();
      GrExpression labelExpression = label.getExpression();

      if (labelName != null) {
        myStringEntries.put(labelName, expression.getType());
      }
      else if (labelExpression != null) {
        PsiType type = labelExpression.getType();
        myOtherEntries.add(new Pair<PsiType, PsiType>(type, expression.getType()));
      }
    }
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return myJavaClassName;
  }

  @NotNull
  public String getClassName() {
    return StringUtil.getShortName(myJavaClassName);
  }

  @Nullable
  public PsiType getTypeByStringKey(String key) {
    return myStringEntries.get(key);
  }

  public Set<String> getStringKeys() {
    return myStringEntries.keySet();
  }

  public PsiType[] getAllKeyTypes() {
    Set<PsiType> result = new HashSet<PsiType>();
    if (!myStringEntries.isEmpty()) {
      result.add(GroovyPsiManager.getInstance(myFacade.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, getResolveScope()));
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
      return EMPTY_ARRAY;
    }

    return new PsiType[]{getLeastUpperBound(keyTypes), getLeastUpperBound(valueTypes)};
  }

  public String getInternalCanonicalText() {
    if (myStringEntries.size() == 0) {
      if (myOtherEntries.size() == 0) return "[:]";
      String name = getJavaClassName();
      final PsiType[] params = getParameters();
      return name + "<" + getInternalText(params[0]) + ", " + getInternalText(params[1]) + ">";
    }

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

  @NotNull
  private static String getInternalText(@Nullable PsiType param) {
    return param == null ? "null" : param.getInternalCanonicalText();
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

  @NotNull
  public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    return new GrMapType(myFacade, getResolveScope(), myStringEntries, myOtherEntries, languageLevel);
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrMapType) {
      return myStringEntries.equals(((GrMapType)obj).myStringEntries) && myOtherEntries.equals(((GrMapType)obj).myOtherEntries);
    }
    return super.equals(obj);
  }

  public boolean isAssignableFrom(@NotNull PsiType type) {
    return type instanceof GrMapType || super.isAssignableFrom(type);
  }

  public static GrMapType merge(GrMapType l, GrMapType r) {
    final GlobalSearchScope scope = l.getScope().intersectWith(r.getResolveScope());

    final Map<String, PsiType> strings = new HashMap<String, PsiType>();
    strings.putAll(l.myStringEntries);
    strings.putAll(r.myStringEntries);

    List<Pair<PsiType, PsiType>> other = new ArrayList<Pair<PsiType, PsiType>>();
    other.addAll(l.myOtherEntries);
    other.addAll(r.myOtherEntries);

    return create(l.myFacade, scope, strings, other);
  }

  public static GrMapType create(JavaPsiFacade facade,
                                 GlobalSearchScope scope,
                                 Map<String, PsiType> stringEntries,
                                 List<Pair<PsiType, PsiType>> otherEntries) {
    return new GrMapType(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }

  public static GrMapType create(GlobalSearchScope scope) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(scope.getProject());
    List<Pair<PsiType, PsiType>> otherEntries = Collections.emptyList();
    Map<String, PsiType> stringEntries = Collections.emptyMap();
    return new GrMapType(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }
}
