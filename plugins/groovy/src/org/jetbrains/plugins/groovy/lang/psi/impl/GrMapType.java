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
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;

import java.util.*;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP;

/**
 * @author peter
 */
public abstract class GrMapType extends GrLiteralClassType {
  private final String myJavaClassName;

  private final VolatileNotNullLazyValue<PsiType[]> myParameters = new VolatileNotNullLazyValue<PsiType[]>() {
    @NotNull
    @Override
    protected PsiType[] compute() {
      final PsiType[] keyTypes = getAllKeyTypes();
      final PsiType[] valueTypes = getAllValueTypes();
      if (keyTypes.length == 0 && valueTypes.length == 0) {
        return EMPTY_ARRAY;
      }

      return new PsiType[]{getLeastUpperBound(keyTypes), getLeastUpperBound(valueTypes)};
    }
  };

  protected GrMapType(JavaPsiFacade facade, GlobalSearchScope scope) {
    this(facade, scope, LanguageLevel.JDK_1_5);
  }

  protected GrMapType(JavaPsiFacade facade,
                      GlobalSearchScope scope,
                      LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);

    myJavaClassName = facade.findClass(JAVA_UTIL_LINKED_HASH_MAP, scope) != null ? JAVA_UTIL_LINKED_HASH_MAP : JAVA_UTIL_MAP;
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return myJavaClassName;
  }

  @Override
  @NotNull
  public String getClassName() {
    return StringUtil.getShortName(myJavaClassName);
  }

  @Nullable
  public abstract PsiType getTypeByStringKey(String key);

  @NotNull
  public abstract Set<String> getStringKeys();

  public abstract boolean isEmpty();

  @NotNull
  protected abstract PsiType[] getAllKeyTypes();

  @NotNull
  protected abstract PsiType[] getAllValueTypes();

  @NotNull
  protected abstract List<Pair<PsiType, PsiType>> getOtherEntries();

  @NotNull
  protected abstract Map<String, PsiType> getStringEntries();

  @Override
  @NotNull
  public PsiType[] getParameters() {
    return myParameters.getValue();
  }

  @Override
  @NotNull
  public String getInternalCanonicalText() {
    Set<String> stringKeys = getStringKeys();
    List<Pair<PsiType, PsiType>> otherEntries = getOtherEntries();

    if (stringKeys.isEmpty()) {
      if (otherEntries.isEmpty()) return "[:]";
      String name = getJavaClassName();
      final PsiType[] params = getParameters();
      return name + "<" + getInternalText(params[0]) + ", " + getInternalText(params[1]) + ">";
    }

    List<String> components = new ArrayList<String>();
    for (String s : stringKeys) {
      components.add("'" + s + "':" + getInternalCanonicalText(getTypeByStringKey(s)));
    }
    for (Pair<PsiType, PsiType> entry : otherEntries) {
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

  public boolean equals(Object obj) {
    if (obj instanceof GrMapType) {
      GrMapType other = (GrMapType)obj;
      return getStringEntries().equals(other.getStringEntries()) &&
             getOtherEntries().equals(other.getOtherEntries());
    }
    return super.equals(obj);
  }

  @Override
  public boolean isAssignableFrom(@NotNull PsiType type) {
    return type instanceof GrMapType || super.isAssignableFrom(type);
  }

  public static GrMapType merge(GrMapType l, GrMapType r) {
    final GlobalSearchScope scope = l.getScope().intersectWith(r.getResolveScope());

    final Map<String, PsiType> strings = new HashMap<String, PsiType>();
    strings.putAll(l.getStringEntries());
    strings.putAll(r.getStringEntries());

    List<Pair<PsiType, PsiType>> other = new ArrayList<Pair<PsiType, PsiType>>();
    other.addAll(l.getOtherEntries());
    other.addAll(r.getOtherEntries());

    return create(l.myFacade, scope, strings, other);
  }

  public static GrMapType create(JavaPsiFacade facade,
                                 GlobalSearchScope scope,
                                 Map<String, PsiType> stringEntries,
                                 List<Pair<PsiType, PsiType>> otherEntries) {
    return new GrMapTypeImpl(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }

  public static GrMapType create(GlobalSearchScope scope) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(scope.getProject());
    List<Pair<PsiType, PsiType>> otherEntries = Collections.emptyList();
    Map<String, PsiType> stringEntries = Collections.emptyMap();
    return new GrMapTypeImpl(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new GrMapTypeImpl(myFacade, getResolveScope(), getStringEntries(), getOtherEntries(), languageLevel);
  }

  public static GrMapType createFromNamedArgs(PsiElement context, GrNamedArgument[] args) {
    return new GrMapTypeFromNamedArgs(context, args);
  }
}
