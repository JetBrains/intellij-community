// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class GrLightParameterListBuilder extends LightElement implements GrParameterList {
  private final List<GrParameter> myParameters = new ArrayList<>();
  private GrParameter[] myCachedParameters;

  public GrLightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public GrParameter addParameter(@NotNull GrParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
    return parameter;
  }

  @Override
  public String toString() {
    return "GrLightParameterListBuilder";
  }

  @NotNull
  @Override
  public GrParameter[] getParameters() {
    if (myCachedParameters == null) {
      if (myParameters.isEmpty()) {
        myCachedParameters = GrParameter.EMPTY_ARRAY;
      }
      else {
        myCachedParameters = myParameters.toArray(GrParameter.EMPTY_ARRAY);
      }
    }
    
    return myCachedParameters;
  }

  public void copyParameters(@NotNull PsiMethod method, PsiSubstitutor substitutor, PsiMethod scope) {
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      GrLightParameter p = new GrLightParameter(StringUtil.notNullize(parameter.getName()), substitutor.substitute(parameter.getType()), scope);

      if (parameter instanceof GrParameter) {
        p.setOptional(((GrParameter)parameter).isOptional());
      }

      addParameter(p);
    }
  }

  @Override
  public int getParameterNumber(GrParameter parameter) {
    return getParameterIndex(parameter);
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    //noinspection SuspiciousMethodCalls
    return myParameters.indexOf(parameter);
  }

  @Override
  public int getParametersCount() {
    return myParameters.size();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameterList(this);
    }
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {

  }

  @NotNull
  public GrParameter removeParameter(int index) {
    GrParameter removed = myParameters.remove(index);
    myCachedParameters = null;
    return removed;
  }

  public void clear() {
    myParameters.clear();
    myCachedParameters = null;
  }
}
