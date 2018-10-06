// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GrMapTypeFromNamedArgs extends GrMapType {

  private final @NotNull LinkedHashMap<String, GrExpression> myStringEntries;
  private final @NotNull List<Couple<GrExpression>> myOtherEntries;

  private final VolatileNotNullLazyValue<List<Couple<PsiType>>> myTypesOfOtherEntries = new VolatileNotNullLazyValue<List<Couple<PsiType>>>() {
    @NotNull
    @Override
    protected List<Couple<PsiType>> compute() {
      return ContainerUtil.map(myOtherEntries, pair -> Couple.of(inferTypePreventingRecursion(pair.first), inferTypePreventingRecursion(pair.second)));
    }
  };

  private final VolatileNotNullLazyValue<LinkedHashMap<String, PsiType>> myTypesOfStringEntries = new VolatileNotNullLazyValue<LinkedHashMap<String,PsiType>>() {
    @NotNull
    @Override
    protected LinkedHashMap<String, PsiType> compute() {
      LinkedHashMap<String, PsiType> result = ContainerUtil.newLinkedHashMap();
      for (Map.Entry<String, GrExpression> entry : myStringEntries.entrySet()) {
        result.put(entry.getKey(), inferTypePreventingRecursion(entry.getValue()));
      }
      return result;
    }

  };

  public GrMapTypeFromNamedArgs(@NotNull PsiElement context, @NotNull GrNamedArgument[] namedArgs) {
    this(JavaPsiFacade.getInstance(context.getProject()), context.getResolveScope(), namedArgs);
  }

  public GrMapTypeFromNamedArgs(@NotNull JavaPsiFacade facade, @NotNull GlobalSearchScope scope, @NotNull GrNamedArgument[] namedArgs) {
    super(facade, scope);

    myStringEntries = ContainerUtil.newLinkedHashMap();
    myOtherEntries = ContainerUtil.newArrayList();
    for (GrNamedArgument namedArg : namedArgs) {
      final GrArgumentLabel label = namedArg.getLabel();
      final GrExpression expression = namedArg.getExpression();
      if (label == null || expression == null) {
        continue;
      }

      final String name = label.getName();
      if (name != null) {
        myStringEntries.put(name, expression);
      }
      else {
        GrExpression labelExpression = label.getExpression();
        if (labelExpression != null) {
          myOtherEntries.add(Couple.of(labelExpression, expression));
        }
      }
    }
  }

  @Nullable
  @Override
  public PsiType getTypeByStringKey(String key) {
    GrExpression expression = myStringEntries.get(key);
    return expression != null ? inferTypePreventingRecursion(expression) : null;
  }

  @NotNull
  @Override
  public Set<String> getStringKeys() {
    return myStringEntries.keySet();
  }

  @Override
  public boolean isEmpty() {
    return myStringEntries.isEmpty() && myOtherEntries.isEmpty();
  }

  @NotNull
  @Override
  protected PsiType[] getAllKeyTypes() {
    Set<PsiType> result = ContainerUtil.newHashSet();
    if (!myStringEntries.isEmpty()) {
      result.add(GroovyPsiManager.getInstance(myFacade.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, getResolveScope()));
    }
    for (Couple<GrExpression> entry : myOtherEntries) {
      result.add(inferTypePreventingRecursion(entry.first));
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @NotNull
  @Override
  protected PsiType[] getAllValueTypes() {
    Set<PsiType> result = ContainerUtil.newHashSet();
    for (GrExpression expression : myStringEntries.values()) {
      result.add(inferTypePreventingRecursion(expression));
    }
    for (Couple<GrExpression> entry : myOtherEntries) {
      result.add(inferTypePreventingRecursion(entry.second));
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @Nullable
  private PsiType inferTypePreventingRecursion(final GrExpression expression) {
    return RecursionManager.doPreventingRecursion(expression, false,
                                                  () -> TypesUtil.boxPrimitiveType(expression.getType(), expression.getManager(), myScope));
  }

  @NotNull
  @Override
  protected List<Couple<PsiType>> getOtherEntries() {
    return myTypesOfOtherEntries.getValue();
  }

  @NotNull
  @Override
  protected LinkedHashMap<String, PsiType> getStringEntries() {
    return myTypesOfStringEntries.getValue();
  }

  @Override
  public boolean isValid() {
    for (GrExpression expression : myStringEntries.values()) {
      if (!expression.isValid()) return false;
    }

    for (Couple<GrExpression> entry : myOtherEntries) {
      if (!entry.first.isValid()) return false;
      if (!entry.second.isValid()) return false;
    }

    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GrMapTypeFromNamedArgs args = (GrMapTypeFromNamedArgs)o;

    if (!myStringEntries.equals(args.myStringEntries)) return false;
    if (!myOtherEntries.equals(args.myOtherEntries)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myStringEntries.hashCode();
    result = 31 * result + myOtherEntries.hashCode();
    return result;
  }
}
