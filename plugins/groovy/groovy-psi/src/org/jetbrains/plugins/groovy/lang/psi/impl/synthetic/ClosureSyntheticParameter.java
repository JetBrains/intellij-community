// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;

import java.util.function.Function;

/**
 * @author ven
 */
public class ClosureSyntheticParameter extends GrLightParameter implements NavigationItem, GrRenameableLightElement {
  private static final Function<ClosureSyntheticParameter,PsiType> TYPES_CALCULATOR = parameter -> {
    PsiType typeGroovy = GrVariableEnhancer.getEnhancedType(parameter);
    if (typeGroovy instanceof PsiIntersectionType) {
      return ((PsiIntersectionType)typeGroovy).getRepresentative();
    }
    return typeGroovy;
  };

  private final GrClosableBlock myClosure;

  public ClosureSyntheticParameter(GrClosableBlock closure, boolean isOptional) {
    super(GrClosableBlock.IT_PARAMETER_NAME, TypesUtil.getJavaLangObject(closure), closure);
    setOptional(isOptional);
    myClosure = closure;
  }

  @Override
  public PsiElement setName(@NotNull String newName) throws IncorrectOperationException {
    if (!newName.equals(getName())) {
      GrParameter parameter = GroovyPsiElementFactory.getInstance(getProject()).createParameter(newName, (String)null, null);
      myClosure.addParameter(parameter);
    }
    return this;
  }

  @Override
  @Nullable
  public PsiType getTypeGroovy() {
    assert isValid();

    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
  }

  @Override
  @Nullable
  public PsiType getDeclaredType() {
    return null;
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return new LocalSearchScope(myClosure);
  }

  public GrClosableBlock getClosure() {
    return myClosure;
  }
}
