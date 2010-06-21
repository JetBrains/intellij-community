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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrVariableEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author ven
 */
public class ClosureSyntheticParameter extends GrLightParameter implements NavigationItem, GrVariableBase {
  private final GrClosableBlock myClosure;

  public ClosureSyntheticParameter(GrClosableBlock closure) {
    super(GrClosableBlock.IT_PARAMETER_NAME, PsiType.getJavaLangObject(closure.getManager(), closure.getResolveScope()), closure);
    myClosure = closure;
    setOptional(true);
  }

  public PsiElement setName(@NotNull String newName) throws IncorrectOperationException {
    if (!newName.equals(getName())) {
      GrParameter parameter = GroovyPsiElementFactory.getInstance(getProject()).createParameter(newName, null, null);
      myClosure.addParameter(parameter);
    }
    return this;
  }

  @Nullable
  public PsiType getTypeGroovy() {
    PsiType typeGroovy = GrVariableEnhancer.getEnhancedType(this);
    if (typeGroovy instanceof PsiIntersectionType) {
      return ((PsiIntersectionType)typeGroovy).getRepresentative();
    }
    return typeGroovy;
  }

  @Nullable
  public PsiType getDeclaredType() {
    return null;
  }

  public boolean isWritable() {
    return true;
  }

  @NotNull
  public SearchScope getUseScope() {
    return new LocalSearchScope(myClosure);
  }

  public GrClosableBlock getClosure() {
    return myClosure;
  }

  @Override
  public PsiElement getContext() {
    return myClosure;
  }

  @Override
  public GrExpression getDefaultInitializer() {
    return GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText("null");
  }
}
