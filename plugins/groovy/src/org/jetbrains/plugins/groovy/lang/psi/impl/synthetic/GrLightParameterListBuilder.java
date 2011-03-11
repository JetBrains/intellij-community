/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
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
  private final List<GrParameter> myParameters = new ArrayList<GrParameter>();
  private GrParameter[] myCachedParameters;

  public GrLightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public void addParameter(GrParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
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
        myCachedParameters = myParameters.toArray(new GrParameter[myParameters.size()]);
      }
    }
    
    return myCachedParameters;
  }

  @Override
  public void addParameterToEnd(GrParameter parameter) {

  }

  @Override
  public void addParameterToHead(GrParameter parameter) {
    throw new IncorrectOperationException("GrLightParameterListBuilder is unmodifiable");
  }

  @Override
  public int getParameterNumber(GrParameter parameter) {
    return getParameterIndex(parameter);
  }

  @Override
  public int getParameterIndex(PsiParameter parameter) {
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
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {

  }
}
