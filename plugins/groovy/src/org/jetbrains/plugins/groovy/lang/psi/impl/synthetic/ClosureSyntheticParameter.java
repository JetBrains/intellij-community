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
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrVariableEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ven
 */
public class ClosureSyntheticParameter extends LightParameter implements NavigationItem, GrVariableBase {
  private final GrClosableBlock myClosure;

  public ClosureSyntheticParameter(PsiManager manager, GrClosableBlock closure) {
    super(manager, GrClosableBlock.IT_PARAMETER_NAME, null, PsiType.getJavaLangObject(manager, closure.getResolveScope()), closure);
    myClosure = closure;
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

  public void accept(GroovyElementVisitor visitor) {
  }

  public boolean isWritable() {
    return true;
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  @NotNull
  public SearchScope getUseScope() {
    return new LocalSearchScope(myClosure);
  }

  public GrClosableBlock getClosure() {
    return myClosure;
  }

  @NotNull
  @Override
  public PsiType getType() {
    return TypesUtil.getJavaLangObject(this);
  }

  @Override
  public PsiElement getContext() {
    return myClosure;
  }

  @Override
  public boolean isOptional() {
    return true;
  }

  @Override
  public GrExpression getDefaultInitializer() {
    return GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText("null");
  }
}
