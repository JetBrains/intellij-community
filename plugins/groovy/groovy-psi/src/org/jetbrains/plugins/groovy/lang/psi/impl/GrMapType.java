// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.*;

public abstract class GrMapType extends GrLiteralClassType {
  private final NotNullLazyValue<PsiType[]> myParameters = NotNullLazyValue.volatileLazy(() -> {
    final PsiType[] keyTypes = getAllKeyTypes();
    final PsiType[] valueTypes = getAllValueTypes();
    if (keyTypes.length == 0 && valueTypes.length == 0) {
      return EMPTY_ARRAY;
    }

    return new PsiType[]{getLeastUpperBound(keyTypes), getLeastUpperBound(valueTypes)};
  });

  protected GrMapType(JavaPsiFacade facade, GlobalSearchScope scope) {
    this(facade, scope, LanguageLevel.JDK_1_5);
  }

  protected GrMapType(JavaPsiFacade facade,
                      GlobalSearchScope scope,
                      @NotNull LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);
  }

  protected GrMapType(@NotNull PsiElement context) {
    super(LanguageLevel.JDK_1_5, context);
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP;
  }

  @Nullable
  public abstract PsiType getTypeByStringKey(String key);

  @NotNull
  public abstract Set<String> getStringKeys();

  public abstract boolean isEmpty();

  protected PsiType @NotNull [] getAllKeyTypes() {
    Set<PsiType> result = new HashSet<>();
    if (!getStringEntries().isEmpty()) {
      result.add(GroovyPsiManager.getInstance(myFacade.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, getResolveScope()));
    }
    for (Couple<PsiType> entry : getOtherEntries()) {
      result.add(entry.first);
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  protected PsiType @NotNull [] getAllValueTypes() {
    Set<PsiType> result = new HashSet<>(getStringEntries().values());
    for (Couple<PsiType> entry : getOtherEntries()) {
      result.add(entry.second);
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @NotNull
  protected abstract List<Couple<PsiType>> getOtherEntries();

  @NotNull
  protected abstract LinkedHashMap<String, PsiType> getStringEntries();

  @Override
  public @Nullable PsiType @NotNull [] getParameters() {
    return myParameters.getValue();
  }

  @NlsSafe
  @Override
  @NotNull
  public String getInternalCanonicalText() {
    Set<String> stringKeys = getStringKeys();
    List<Couple<PsiType>> otherEntries = getOtherEntries();

    if (stringKeys.isEmpty()) {
      if (otherEntries.isEmpty()) return "[:]";
      String name = getJavaClassName();
      final PsiType[] params = getParameters();
      if (params.length == 2) {
        return name + "<" + getInternalText(params[0]) + ", " + getInternalText(params[1]) + ">";
      }
      else {
        return name;
      }
    }

    List<String> components = new ArrayList<>();
    for (String s : stringKeys) {
      components.add("'" + s + "':" + getInternalCanonicalText(getTypeByStringKey(s)));
    }
    for (Couple<PsiType> entry : otherEntries) {
      components.add(getInternalCanonicalText(entry.first) + ":" + getInternalCanonicalText(entry.second));
    }
    boolean tooMany = components.size() > 2;
    final List<String> theFirst = ContainerUtil.getFirstItems(components, 2);
    return "[" + StringUtil.join(theFirst, ", ") + (tooMany ? ",..." : "") + "]";
  }

  @NlsSafe
  @NotNull
  private static String getInternalText(@Nullable PsiType param) {
    return param == null ? "null" : param.getInternalCanonicalText();
  }

  @Override
  public boolean isAssignableFrom(@NotNull PsiType type) {
    return type instanceof GrMapType || super.isAssignableFrom(type);
  }

  public static GrMapType merge(GrMapType l, GrMapType r) {
    final GlobalSearchScope scope = l.getResolveScope().intersectWith(r.getResolveScope());

    final LinkedHashMap<String, PsiType> strings = new LinkedHashMap<>();
    strings.putAll(l.getStringEntries());
    strings.putAll(r.getStringEntries());

    List<Couple<PsiType>> other = new ArrayList<>();
    other.addAll(l.getOtherEntries());
    other.addAll(r.getOtherEntries());

    return create(l.myFacade, scope, strings, other);
  }

  public static GrMapType create(JavaPsiFacade facade,
                                 GlobalSearchScope scope,
                                 @NotNull LinkedHashMap<String, PsiType> stringEntries,
                                 @NotNull List<Couple<PsiType>> otherEntries) {
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

  @NonNls
  @Override
  public String toString() {
    return "map type";
  }
}
