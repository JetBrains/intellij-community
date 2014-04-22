/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Max Medvedev on 07/04/14
 */
public class GrMapTypeFromNamedArgs extends GrMapType {

  private final Map<String, GrExpression> myStringEntries;
  private final List<Pair<GrExpression, GrExpression>> myOtherEntries;

  private final VolatileNotNullLazyValue<List<Pair<PsiType, PsiType>>> myTypesOfOtherEntries = new VolatileNotNullLazyValue<List<Pair<PsiType, PsiType>>>() {
    @NotNull
    @Override
    protected List<Pair<PsiType, PsiType>> compute() {
      return ContainerUtil.map(myOtherEntries, new Function<Pair<GrExpression, GrExpression>, Pair<PsiType, PsiType>>() {
        @Override
        public Pair<PsiType, PsiType> fun(Pair<GrExpression, GrExpression> pair) {
          return Pair.create(pair.first.getType(), pair.second.getType());
        }
      });
    }
  };

  private final VolatileNotNullLazyValue<Map<String, PsiType>> myTypesOfStringEntries = new VolatileNotNullLazyValue<Map<String,PsiType>>() {
    @NotNull
    @Override
    protected Map<String, PsiType> compute() {
      HashMap<String, PsiType> result = ContainerUtil.newHashMap();
      for (Map.Entry<String, GrExpression> entry : myStringEntries.entrySet()) {
        result.put(entry.getKey(), entry.getValue().getType());
      }
      return result;
    }

  };

  public GrMapTypeFromNamedArgs(@NotNull PsiElement context, @NotNull GrNamedArgument[] namedArgs) {
    this(JavaPsiFacade.getInstance(context.getProject()), context.getResolveScope(), namedArgs);
  }

  public GrMapTypeFromNamedArgs(@NotNull JavaPsiFacade facade, @NotNull GlobalSearchScope scope, @NotNull GrNamedArgument[] namedArgs) {
    super(facade, scope);

    myStringEntries = ContainerUtil.newHashMap();
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
      else if (label.getExpression() != null) {
        myOtherEntries.add(Pair.create(label.getExpression(), expression));
      }
    }
  }

  @Nullable
  @Override
  public PsiType getTypeByStringKey(String key) {
    GrExpression expression = myStringEntries.get(key);
    return expression != null ? expression.getType() : null;
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
    for (Pair<GrExpression, GrExpression> entry : myOtherEntries) {
      result.add(entry.first.getType());
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @NotNull
  @Override
  protected PsiType[] getAllValueTypes() {
    Set<PsiType> result = ContainerUtil.newHashSet();
    for (GrExpression expression : myStringEntries.values()) {
      result.add(expression.getType());
    }
    for (Pair<GrExpression, GrExpression> entry : myOtherEntries) {
      result.add(entry.second.getType());
    }
    result.remove(null);
    return result.toArray(createArray(result.size()));
  }

  @NotNull
  @Override
  protected List<Pair<PsiType, PsiType>> getOtherEntries() {
    return myTypesOfOtherEntries.getValue();
  }

  @NotNull
  @Override
  protected Map<String, PsiType> getStringEntries() {
    return myTypesOfStringEntries.getValue();
  }

  @Override
  public boolean isValid() {
    for (GrExpression expression : myStringEntries.values()) {
      if (!expression.isValid()) return false;
    }

    for (Pair<GrExpression, GrExpression> entry : myOtherEntries) {
      if (!entry.first.isValid()) return false;
      if (!entry.second.isValid()) return false;
    }

    return true;
  }
}
