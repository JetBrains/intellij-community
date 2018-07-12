// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.*;

/**
 * @author peter
 */
public abstract class GrMapType extends GrLiteralClassType {

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
                      @NotNull LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);
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

  @NotNull
  protected abstract PsiType[] getAllKeyTypes();

  @NotNull
  protected abstract PsiType[] getAllValueTypes();

  @NotNull
  protected abstract List<Couple<PsiType>> getOtherEntries();

  @NotNull
  protected abstract LinkedHashMap<String, PsiType> getStringEntries();

  @Override
  @NotNull
  public PsiType[] getParameters() {
    return myParameters.getValue();
  }

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

    final LinkedHashMap<String, PsiType> strings = ContainerUtil.newLinkedHashMap();
    strings.putAll(l.getStringEntries());
    strings.putAll(r.getStringEntries());

    List<Couple<PsiType>> other = new ArrayList<>();
    other.addAll(l.getOtherEntries());
    other.addAll(r.getOtherEntries());

    return create(l.myFacade, scope, strings, other);
  }

  public static GrMapType create(JavaPsiFacade facade,
                                 GlobalSearchScope scope,
                                 LinkedHashMap<String, PsiType> stringEntries,
                                 List<Couple<PsiType>> otherEntries) {
    return new GrMapTypeImpl(facade, scope, stringEntries, otherEntries, LanguageLevel.JDK_1_5);
  }

  public static GrMapType create(GlobalSearchScope scope) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(scope.getProject());
    List<Couple<PsiType>> otherEntries = Collections.emptyList();
    LinkedHashMap<String, PsiType> stringEntries = ContainerUtil.newLinkedHashMap();
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

  @Override
  public String toString() {
    return "map type";
  }
}
