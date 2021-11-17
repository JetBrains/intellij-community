// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.*;
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

  private final SmartPsiElementPointer<GrClosableBlock> myClosure;

  public ClosureSyntheticParameter(@NotNull GrClosableBlock closure, boolean isOptional) {
    super(GrClosableBlock.IT_PARAMETER_NAME, TypesUtil.getJavaLangObject(closure), closure);
    setOptional(isOptional);
    myClosure = SmartPointerManager.createPointer(closure);
  }

  @Override
  public PsiElement getParent() {
    return myClosure.getElement();
  }

  @Override
  public PsiElement setName(@NotNull String newName) throws IncorrectOperationException {
    if (!newName.equals(getName())) {
      GrParameter parameter = GroovyPsiElementFactory.getInstance(getProject()).createParameter(newName, (String)null, null);
      GrClosableBlock closure = myClosure.getElement();
      if (closure == null) {
        throw new IncorrectOperationException("Invalidated element pointer");
      }
      closure.addParameter(parameter);
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
    GrClosableBlock closure = myClosure.getElement();
    if (closure == null) {
      throw new IncorrectOperationException("Pointer is invalidated");
    }
    return new LocalSearchScope(closure);
  }

  public boolean isStillValid() {
    return myClosure.getElement() != null;
  }

  public GrClosableBlock getClosure() {
    GrClosableBlock closure = myClosure.getElement();
    if (closure == null) {
      throw new IncorrectOperationException("Pointer is invalidated");
    }
    return closure;
  }
}
